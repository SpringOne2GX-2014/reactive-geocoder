package demo;

import demo.domain.Location;
import demo.domain.LocationRepository;
import demo.geo.GeoNearService;
import demo.geo.HaversinePredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoModule;
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

import java.util.function.Function;

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
	public Function<Location, Stream<Location>> streamFactory(Environment env,
	                                                          LocationRepository locations,
	                                                          GeoNearService geoNear) {
		return loc1 -> Streams.<Location>defer(env)
		                      .consume(loc2 -> log.info("loc1: {}, loc2: {}", loc1, loc2))
		                      .filter(loc2 -> {
			                      return !loc1.getId().equals(loc2.getId());
		                      })
		                      .consume(loc2 -> log.info("after filter[0]: {}", loc2))
		                      .filter(new HaversinePredicate(loc1.getCoordinates(), new Distance(10)))
		                      .consume(loc2 -> log.info("after filter[1]: {}", loc2))
		                      .consume(loc2 -> geoNear.addGeoNear(loc1, loc2));
	}

	@Bean
	public Action<Chain> handlers(ProcessorRestApi restApi) {
		return (chain) -> {
			// GET '/'
			chain.get(restApi.root());

			// POST '/location'
			chain.post("location", restApi.postLocation());

			// REST '/location/:id'
			chain.handler("location/:id", restApi.location());
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
