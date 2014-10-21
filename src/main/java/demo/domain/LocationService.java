package demo.domain;

import demo.geo.GeoNearPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.stereotype.Service;
import reactor.core.Environment;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.stream.HotStream;

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

	private final Environment         env;
	private final LocationRepository  locations;
	private final HotStream<Location> locationSaveEvents;

	@Autowired
	public LocationService(Environment env,
	                       LocationRepository locations,
	                       HotStream<Location> locationSaveEvents) {
		this.env = env;
		this.locations = locations;
		this.locationSaveEvents = locationSaveEvents;

		locations.deleteAll();
	}

	public Map<String, Stream<Location>> registry() {
		return this.nearbyStreams;
	}

	public Stream<Location> findOne(String id) {
		return Streams.just(id)
		              .dispatchOn(env, env.getDefaultDispatcherFactory().get())
		              .<Location>map(locations::findOne);
	}

	public Stream<Location> update(Location loc) {
		return Streams.just(loc)
				.dispatchOn(env, env.getDefaultDispatcherFactory().get())

						// persist incoming to MongoDB
				.map(locations::save)

						// broadcast this update to others
				.observe(locationSaveEvents::broadcastNext);
	}

	public Stream<Location> nearby(String myLocId, int distance) {
		Stream<Location> s = findOne(myLocId);

		return s.flatMap(myLoc ->
				                 // merge historical and live data
				                 Streams.merge(locationSaveEvents,
				                               Streams.defer(locations.findAll()))
						                 .dispatchOn(env, s.getDispatcher())

								                 // not us
						                 .filter(l -> !nullSafeEquals(l.getId(), myLocId))

								                 // only Locations within given Distance
						                 .filter(new GeoNearPredicate(myLoc.toPoint(), new Distance(distance)))
		);
	}

}
