package demo;

import demo.domain.Location;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import ratpack.error.ServerErrorHandler;
import ratpack.exec.ExecInterceptor;
import ratpack.exec.Execution;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.handling.RequestOutcome;
import ratpack.http.Request;
import ratpack.jackson.Jackson;
import ratpack.render.Renderer;
import ratpack.render.RendererSupport;
import ratpack.spring.annotation.EnableRatpack;
import ratpack.util.internal.NumberUtil;
import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.spec.Reactors;
import reactor.rx.Stream;
import reactor.rx.spec.Streams;
import reactor.spring.context.config.EnableReactor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
	public Renderer<Location> locationRenderer() {
		return new RendererSupport<Location>() {
			@Override
			public void render(Context context, Location location) throws Exception {
				context.render(Jackson.json(location));
			}
		};
	}

	@Bean
	public Action<Chain> handlers(ProcessorRestApi restApi) {
		final AtomicInteger counter = new AtomicInteger();

		return (chain) -> {
			chain.register(registrySpec -> registrySpec.add(ServerErrorHandler.class, (context, exception) -> {
				exception.printStackTrace();
				context.render("error");
			}));

			chain.handler(ctx -> {
				long startAt = System.nanoTime();
				ctx.addInterceptor(new ProcessingTimingInterceptor(ctx.getRequest()), execution -> {
					ctx.onClose(requestOutcome -> {
						Timer timer = ctx.getRequest().get(Timer.class);
						System.out.println(counter.getAndIncrement() + " - compute: " + timer.getComputeTime() + ", blocking: " + timer.getBlockingTime() + ", total: " + (NumberUtil.toMillisDiffString(startAt, requestOutcome.getClosedAt())));
					});
					ctx.next();
				});
			});

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

	public static class Timer {

		private final AtomicLong totalCompute = new AtomicLong();
		private final AtomicLong totalBlocking = new AtomicLong();
		private boolean blocking;
		private final ThreadLocal<Long> startedAt = new ThreadLocal<Long>() {
			protected Long initialValue() {
				return 0l;
			}
		};
		public void start(boolean blocking) {
			this.blocking = blocking;
			startedAt.set(System.currentTimeMillis());
		}
		public void stop() {
			long startedAtTime = startedAt.get();
			startedAt.remove();
			AtomicLong counter = blocking ? totalBlocking : totalCompute;
			counter.addAndGet(startedAtTime > 0 ? System.currentTimeMillis() - startedAtTime : 0);
		}
		public long getBlockingTime() {
			return totalBlocking.get();
		}
		public long getComputeTime() {
			return totalCompute.get();
		}
	}

	public static class ProcessingTimingInterceptor implements ExecInterceptor {
		private final Request request;

		public ProcessingTimingInterceptor(Request request) {
			this.request = request;
			request.register(new Timer());
		}

		public void intercept(ExecInterceptor.ExecType type, Runnable continuation) {
			Timer timer = request.get(Timer.class);
			timer.start(type.equals(ExecInterceptor.ExecType.BLOCKING));
			continuation.run();
			timer.stop();
		}
	}

}
