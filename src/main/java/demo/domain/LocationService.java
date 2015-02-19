package demo.domain;

import demo.geo.GeoNearPredicate;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.stereotype.Service;
import reactor.Environment;
import reactor.fn.Consumer;
import reactor.fn.Supplier;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.action.terminal.ObservableAction;
import reactor.rx.broadcast.Broadcaster;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static reactor.core.support.ObjectUtils.nullSafeEquals;


/**
 * @author Jon Brisbin
 */
@Service
public class LocationService {

    private final ConcurrentHashMap<String, Stream<Location>> nearbyStreams = new ConcurrentHashMap<>();

    private final LocationRepository locations;
    private final Broadcaster<Location> locationSaveEvents;

    @Autowired
    public LocationService(
            LocationRepository locations,
            Broadcaster<Location> locationSaveEvents) {
        this.locations = locations;
        this.locationSaveEvents = locationSaveEvents;

        locations.deleteAll();
    }

    public Map<String, Stream<Location>> registry() {
        return this.nearbyStreams;
    }

    public Stream<Location> findOne(String id) {
        return Streams.just(id)
                .dispatchOn(Environment.cachedDispatcher())
                .<Location>map(locations::findOne);
    }

    public Stream<Location> update(Location loc) {
        return Streams.just(loc)
                .dispatchOn(Environment.cachedDispatcher())

                // persist incoming to MongoDB
                .map(locations::save)

                // broadcast this update to others
                .observe(locationSaveEvents::onNext);
    }

    public Stream<Location> nearby(String myLocId, int distance) {
        Stream<Location> s = findOne(myLocId);

        return s.flatMap(myLoc ->
                        // merge historical and live data
                        Streams.merge(locationSaveEvents,
                                Streams.from(locations.findAll())
                                .dispatchOn(Environment.cachedDispatcher())

                                // not us
                                .filter(l -> !nullSafeEquals(l.getId(), myLocId))

                                // only Locations within given Distance
                                .filter(new GeoNearPredicate(myLoc.toPoint(), new Distance(distance)))
        ));
    }

}
