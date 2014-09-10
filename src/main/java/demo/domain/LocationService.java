package demo.domain;

import demo.geo.GeoNearPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.stereotype.Service;
import reactor.core.Environment;
import reactor.event.dispatch.Dispatcher;
import reactor.function.Consumer;
import reactor.rx.Stream;
import reactor.rx.action.Action;
import reactor.rx.spec.Streams;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static reactor.util.ObjectUtils.nullSafeEquals;

/**
 * @author Jon Brisbin
 */
@Service
public class LocationService {

	private final Logger                                      log           = LoggerFactory.getLogger(getClass());
	private final ConcurrentHashMap<String, Stream<Location>> nearbyStreams = new ConcurrentHashMap<>();

	private final Environment        env;
	private final LocationRepository locations;
	private final Stream<Location>   locationSaveEvents;

	@Autowired
	public LocationService(Environment env,
	                       LocationRepository locations,
	                       Stream<Location> locationSaveEvents) {
		this.env = env;
		this.locations = locations;
		this.locationSaveEvents = locationSaveEvents;

		locations.deleteAll();
	}

	public Map<String, Stream<Location>> registry() {
		return this.nearbyStreams;
	}

	public Action<String, Location> findOne(String id) {
		return Streams.defer(env, env.getDefaultDispatcherFactory().get(), id)
		              .<Location>map(locations::findOne);
	}

	public Stream<Location> update(Location loc) {
		return Streams.defer(env, env.getDefaultDispatcherFactory().get(), loc)

				// persist incoming to MongoDB
				.map(locations::save)

						// broadcast this update to others
				.observe(locationSaveEvents::broadcastNext);
	}

	public Stream<Location> nearby(String locId, int distance, Consumer<Location> sink) {
		final Dispatcher dispatcher = env.getDefaultDispatcherFactory().get();

		// merge existing nearby Locations with live events
		return Streams.defer(env, dispatcher, locId)
		              .<Location>map(locations::findOne)
		              .map(l -> new GeoNearPredicate(l.toPoint(), new Distance(distance)))
		              .observe(l -> log.info("after predicate: {}", l))
		              .flatMap(filter -> {
			              log.info("before merge...");
			              return Streams.merge(env, dispatcher, locationSaveEvents, Streams.defer(locations.findAll()))
					              .observe(l -> log.info("nearby: {}", l))
							              // filter out our own Location
					              .filter(nearbyLoc -> !nullSafeEquals(nearbyLoc.getId(), locId))
					              .observe(l -> log.info("after !me filter"))

							              // filter out only Locations within given Distance
					              .filter(filter);
		              });
	}

}
