package demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.domain.Location;
import demo.domain.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import reactor.rx.spec.Streams;
import reactor.spring.context.config.EnableReactor;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;
import static ratpack.websocket.WebSockets.websocketBroadcast;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableConfigurationProperties
@EnableMongoRepositories
@EnableRatpack
@EnableReactor
public class ProcessorApplication {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Bean
	public Stream<Location> locationEventStream(Environment env) {
		return Streams.defer(env);
	}

	@Bean
	public Action<Chain> handlers(Environment env,
	                              LocationService locations,
	                              ObjectMapper mapper) {
		return (chain) -> {
			chain.get(ctx -> ctx.render(ctx.file("public/index.html")));

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

			chain.handler("location/:id/live", ctx -> {
				String id = ctx.getPathTokens().get("id");

				Stream<Location> nearby;
				if (null != (nearby = locations.nearbyAsStream(id))) {
					websocketBroadcast(ctx, nearby.map(l -> l.toJson(mapper)));
				} else {
					ctx.redirect(303, "/location/" + id + "/live");
				}
			});

			// Find nearby Locations
			chain.get("location/:id/nearby", ctx -> {
				String id = ctx.getPathTokens().get("id");

				ctx.render(json(locations.nearby(id)));
			});
		};
	}

	private static WebSocketHandler<Stream<Location>> webSocketHandler(String id,
	                                                                   LocationService locations,
	                                                                   ObjectMapper mapper) {
		return new WebSocketHandler<Stream<Location>>() {
			@Override
			public Stream<Location> onOpen(WebSocket ws) throws Exception {
				ws.send(mapper.writeValueAsString(locations.nearby(id)));

				return locations.nearbyAsStream(id)
				                .observe(l -> ws.send(l.toJson(mapper)));
			}

			@Override
			public void onClose(WebSocketClose<Stream<Location>> close) throws Exception {

			}

			@Override
			public void onMessage(WebSocketMessage<Stream<Location>> frame) throws Exception {

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
