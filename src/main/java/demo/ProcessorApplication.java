package demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
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
import ratpack.path.PathBinding;
import ratpack.path.internal.DefaultPathBinding;
import ratpack.render.Renderer;
import ratpack.render.RendererSupport;
import ratpack.spring.annotation.EnableRatpack;
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

				Stream<Location> nearby;
				if (null != (nearby = locations.nearby(id))) {
					websocketBroadcast(ctx, nearby.map(l -> l.toJson(mapper)));
				} else {
					// We know that bad ids won't be requested
					ctx.redirect(303, ctx.getRequest().getUri());
				}
			});

			chain.assets("src/main/resources/public", "index.html");
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
