package demo;

import demo.domain.Location;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.geo.GeoModule;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.spring.annotation.EnableRatpack;
import reactor.core.Environment;
import reactor.rx.Stream;
import reactor.rx.spec.Streams;
import reactor.spring.context.config.EnableReactor;

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
		return Streams.defer(env, env.getDispatcher(Environment.RING_BUFFER));
	}

	@Bean
	public Action<Chain> handlers(ProcessorRestApi restApi) {
		return (chain) -> {
			chain.get(ctx -> ctx.render(ctx.file("public/index.html")));

			// Create new Location
			chain.post("location", restApi.createLocation());

			// Retrieve existing Location
			chain.get("location/:id", restApi.retrieveLocation());

			// Find nearby Locations
			chain.get("location/:id/nearby", restApi.retrieveNearby());
		};
	}

	@Bean
	public GeoModule geoJacksonModule() {
		return new GeoModule();
	}

	public static void main(String[] args) {
		SpringApplication.run(ProcessorApplication.class, args);
	}

}
