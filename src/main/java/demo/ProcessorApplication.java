package demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.domain.Location;
import demo.domain.LocationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.geo.Distance;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.render.Renderer;
import ratpack.render.RendererSupport;
import ratpack.spring.annotation.EnableRatpack;
import ratpack.websocket.WebSocket;
import ratpack.websocket.WebSocketClose;
import ratpack.websocket.WebSocketHandler;
import ratpack.websocket.WebSocketMessage;
import reactor.core.Environment;
import reactor.rx.Stream;
import reactor.rx.action.CallbackAction;
import reactor.rx.spec.Streams;
import reactor.spring.context.config.EnableReactor;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;
import static ratpack.websocket.WebSockets.websocket;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableConfigurationProperties
@EnableMongoRepositories
@EnableRatpack
@EnableReactor
public class ProcessorApplication {

	@Bean
	public Stream<Location> locationEventStream(Environment env) {
		return Streams.defer(env);
	}

	@Bean
	public Action<Chain> handlers(LocationService locations, ObjectMapper mapper) {
		return (chain) -> {
			// Create new Location
			chain.post("location", ctx -> {
				Location loc = ctx.parse(fromJson(Location.class));

				ctx.promise(f -> locations.create(loc)
				                          .consume(f::success))
				   .then(ctx::render);
			});

			// Update existing Location
			chain.put("location/:id", ctx -> {
				String id = ctx.getPathTokens().get("id");
				int distance = Integer.valueOf(ctx.getRequest()
				                                  .getQueryParams()
				                                  .get("distance"));
				Location inLoc = ctx.parse(fromJson(Location.class));

				ctx.promise(f -> locations.findOne(id)
				                          .flatMap(l -> locations.update(l.setName(inLoc.getName())
				                                                          .setAddress(inLoc.getAddress())
				                                                          .setCity(inLoc.getCity())
				                                                          .setProvince(inLoc.getProvince())
				                                                          .setPostalCode(inLoc.getPostalCode())
				                                                          .setCoordinates(inLoc.getCoordinates()),
				                                                         new Distance(distance)))
				                          .consume(f::success))
				   .then(ctx::render);
			});

			// Watch for updates of Locations near us
			chain.handler("location/:id/nearby", ctx -> {
				String id = ctx.getPathTokens().get("id");

				websocket(ctx, webSocketHandler(locations, mapper, id));
			});
		};
	}

	private WebSocketHandler<?> webSocketHandler(LocationService locations,
	                                             ObjectMapper mapper,
	                                             String id) {
		return new WebSocketHandler<CallbackAction<String>>() {
			@Override
			public CallbackAction<String> onOpen(WebSocket ws) throws Exception {
				return locations.nearby(id)
				                .map(l -> l.toJson(mapper))
				                .consume(ws::send);
			}

			@Override
			public void onClose(WebSocketClose<CallbackAction<String>> close) throws Exception {
				close.getOpenResult().cancel();
			}

			@Override
			public void onMessage(WebSocketMessage<CallbackAction<String>> frame) throws Exception {

			}
		};
	}

	@Bean
	public Renderer<Location> locationRenderer() {
		return new RendererSupport<Location>() {
			@Override
			public void render(Context ctx, Location loc) throws Exception {
				ctx.render(json(loc));
			}
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(ProcessorApplication.class, args);
	}

}
