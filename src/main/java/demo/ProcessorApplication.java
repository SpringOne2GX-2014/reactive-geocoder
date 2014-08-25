package demo;

import demo.domain.Location;
import demo.domain.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import reactor.core.Reactor;
import reactor.core.spec.Reactors;
import reactor.event.registry.Registration;
import reactor.function.Consumer;
import reactor.rx.Stream;
import reactor.rx.spec.Streams;
import reactor.spring.context.config.EnableReactor;

import java.util.concurrent.TimeUnit;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableConfigurationProperties
@EnableMongoRepositories
@EnableRatpack
@EnableReactor
public class ProcessorApplication {

	private Logger log = LoggerFactory.getLogger(getClass());

	@Bean
	public Reactor eventBus(Environment env) {
		return Reactors.reactor(env, Environment.WORK_QUEUE);
	}

	@Bean
	public Stream<Location> locationEventStream(Environment env) {
		return Streams.defer(env);
	}

	@Bean
	public Registration<? extends Consumer<Long>> periodicRetriever(Environment env,
	                                                                LocationRepository locations,
	                                                                Stream<Location> locationStream) {
		return env.getRootTimer()
		          .schedule(now -> locations.findAll().forEach(locationStream::broadcastNext),
		                    5, TimeUnit.SECONDS);
	}

	@Bean
	public Action<Chain> handlers(ProcessorRestApi restApi) {
		return (chain) -> {
			// GET '/'
			chain.get(restApi.root());

			// POST '/location'
			chain.post("location", restApi.postLocation());

			// GET '/location/:id'
			chain.get("location/:id", restApi.location());

			chain.get("location/:id/nearby", restApi.nearby());
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
