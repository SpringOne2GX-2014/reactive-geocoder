package demo;

import demo.domain.Location;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.spring.annotation.EnableRatpack;
import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.spec.Reactors;
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
	public Reactor eventBus(Environment env) {
		return Reactors.reactor(env);
	}

	@Bean
	public Stream<Location> locationEventStream(Environment env) {
		return Streams.defer(env);
	}

	@Bean
	public Action<Chain> handlers(ProcessorRestApi restApi) {
		return (chain) -> {
			chain.get(ctx -> ctx.render(ctx.file("public/index.html")));

			// Create new Location
			chain.post("location", restApi.createLocation());

			// REST for Location
			chain.handler("location/:id", ctx -> ctx.byMethod(spec -> {
				spec.get(restApi.retrieveLocation());
				spec.put(restApi.updateLocation());
			}));

			// Find nearby Locations
			chain.get("location/:id/nearby", restApi.retrieveNearby());
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(ProcessorApplication.class, args);
	}

}
