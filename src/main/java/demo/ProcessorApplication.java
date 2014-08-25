package demo;

import demo.geo.GeocodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ratpack.spring.annotation.EnableRatpack;
import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.spec.Reactors;
import reactor.event.Event;
import reactor.spring.context.config.EnableReactor;

import java.io.IOException;

import static reactor.event.selector.Selectors.$;
import static reactor.event.selector.Selectors.T;

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
	public Reactor eventBus(Environment env,
	                        GeocodeService geocode) {
		Reactor reactor = Reactors.reactor(env, Environment.WORK_QUEUE);

		reactor.on(T(IOException.class), (Event<IOException> ev) -> {
			log.error(ev.getData().getMessage(), ev.getData());
		});

		reactor.on($("geocode"), (Event<Context> ev) -> {
			try {
				geocode.geocode("394 SE 1st Lane", "Lamar", "MO", "64759");
			} catch (IOException e) {
				reactor.notify(IOException.class, Event.wrap(e));
			}
		});

		return reactor;
	}

	@Bean
	public Action<Chain> handlers(RestApi restApi) {
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

	public static void main(String[] args) {
		SpringApplication.run(ProcessorApplication.class, args);
	}

}
