package demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gs.collections.impl.map.mutable.UnifiedMap;
import demo.domain.Location;
import demo.domain.LocationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.render.Renderer;
import ratpack.render.RendererSupport;
import ratpack.spring.annotation.EnableRatpack;
import ratpack.util.MultiValueMap;
import ratpack.websocket.WebSocket;
import ratpack.websocket.WebSocketClose;
import ratpack.websocket.WebSocketHandler;
import ratpack.websocket.WebSocketMessage;
import reactor.core.Environment;
import reactor.rx.Stream;
import reactor.rx.action.CallbackAction;
import reactor.rx.spec.Streams;
import reactor.spring.context.config.EnableReactor;

import java.util.Map;

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
	public Action<Chain> handlers(LocationService locations,
	                              ObjectMapper mapper) {
		return (chain) -> {
			// Create new Location
			chain.post("location", ctx -> {
				Location loc = ctx.parse(fromJson(Location.class));

				ctx.promise(f -> locations.update(loc)
				                          .consume(f::success))
				   .then(ctx::render);
			});

			// Update existing Location
			chain.put("location/:id", ctx -> {
				String id = ctx.getPathTokens().get("id");
				Location inLoc = ctx.parse(fromJson(Location.class));

				ctx.promise(f -> locations.findOne(id)
				                          .flatMap(l -> locations.update(l.setName(inLoc.getName())
				                                                          .setAddress(inLoc.getAddress())
				                                                          .setCity(inLoc.getCity())
				                                                          .setProvince(inLoc.getProvince())
				                                                          .setPostalCode(inLoc.getPostalCode())
				                                                          .setCoordinates(inLoc.getCoordinates())))
				                          .consume(f::success))
				   .then(ctx::render);
			});

			// Watch for updates of Locations near us
			chain.handler("location/:id/nearby", ctx -> {
				String id = ctx.getPathTokens().get("id");
				MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
				int distance = (params.containsKey("distance") ? Integer.valueOf(params.get("distance")) : 20);

				websocket(ctx, webSocketHandler(locations, mapper, id, distance));
			});

			chain.get("debug", ctx -> {
				Map<String, Object> m = UnifiedMap.newMap();
				for (Map.Entry<String, Stream<Location>> e : locations.registry().entrySet()) {
					m.put(e.getKey(), e.getValue().debug().toMap());
				}
				ctx.render(json(m));
			});
		};
	}

	private static WebSocketHandler<?> webSocketHandler(LocationService locations,
	                                                    ObjectMapper mapper,
	                                                    String id,
	                                                    int distance) {
		return new WebSocketHandler<Stream<Location>>() {
			@Override
			public Stream<Location> onOpen(WebSocket ws) throws Exception {
				return locations.nearby(id, distance, null)
						.consume(l -> ws.send(l.toJson(mapper)));
			}

			@Override
			public void onClose(WebSocketClose<Stream<Location>> close) throws Exception {
				close.getOpenResult().cancel();
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
