package demo;

import demo.domain.Location;
import demo.domain.LocationRepository;
import demo.geo.GeoNearPredicate;
import demo.geo.GeoNearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Component;
import ratpack.exec.Promise;
import ratpack.func.Action;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.event.Event;
import reactor.rx.Stream;
import reactor.rx.spec.Streams;
import reactor.tuple.Tuple;

import java.util.List;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;
import static reactor.event.selector.Selectors.$;
import static reactor.util.ObjectUtils.nullSafeEquals;

/**
 * @author Jon Brisbin
 */
@Component
public class ProcessorRestApi {

	private final LocationRepository locations;
	private final GeoNearService     geoNear;
	private final Environment        env;
	private final Reactor            eventBus;
	private final Stream<Location>   locationEventStream;
	private final ProcessorConfig    config;
	private final Distance           defaultDistance;

	@Autowired
	public ProcessorRestApi(LocationRepository locations,
	                        GeoNearService geoNear,
	                        Environment env,
	                        Reactor eventBus,
	                        Stream<Location> locationEventStream,
	                        ProcessorConfig config) {
		this.locations = locations;
		this.geoNear = geoNear;
		this.env = env;
		this.eventBus = eventBus;
		this.locationEventStream = locationEventStream;
		this.config = config;
		this.defaultDistance = new Distance(config.getDefaultDistance());
	}

	public Handler createLocation() {
		return ctx -> {
			// Save a new Location
			Location location = ctx.parse(fromJson(Location.class));
			ctx.blocking(() -> locations.save(location)).then(loc -> {
				// Broadcast to others
				locationEventStream.broadcastNext(loc);

				// Only add Locations <= 10km away from my Location
				Point p = new Point(loc.getCoordinates()[0], loc.getCoordinates()[1]);
				GeoNearPredicate filter = new GeoNearPredicate(p, defaultDistance);

				ctx.blocking(() -> (List<Location>) locations.findAll()).then(prev -> {
					Stream<Location> sink;
					if (prev.isEmpty()) {
						sink = locationEventStream;
					} else {
						sink = Streams.merge(env, locationEventStream, Streams.defer(prev));
					}
					sink
							.filter(l -> !nullSafeEquals(loc.getId(), l.getId())) // not us
							.filter(filter)
							.consume(loc2 -> geoNear.addGeoNear(loc, loc2)); // add to cache

					// Listen for changes to distance value
					eventBus.on($(loc.getId() + ".distance"), filter);

					// Redirect to REST URL
					ctx.redirect(303, config.getBaseUri() + "/location/" + loc.getId());
				});
			});

		};
	}

	public Handler updateLocation() {
		return ctx -> findLocation(ctx).then(loc -> {
			if (loc == null) {
				ctx.clientError(404);
				return;
			}

			// Update Location
			Location inLoc = ctx.parse(fromJson(Location.class));
			loc.setName(inLoc.getName())
					.setAddress(inLoc.getAddress())
					.setCity(inLoc.getCity())
					.setProvince(inLoc.getProvince())
					.setPostalCode(inLoc.getPostalCode())
					.setCoordinates(inLoc.getCoordinates());

			final Location finalLoc = loc;
			ctx.blocking(() -> locations.save(finalLoc)).then((savedLoc) -> {
				// Update distance
				int distance = Integer.parseInt(ctx.getRequest()
						.getQueryParams()
						.get("distance"));
				Point p = new Point(loc.getCoordinates()[0], loc.getCoordinates()[1]);
				Distance d = new Distance(distance);

				// Notify Predicate of the change
				eventBus.notify(loc.getId() + ".distance", Event.wrap(Tuple.of(p, d)));

				// Clear cache
				geoNear.clearGeoNear(loc);

				// Find nearby by querying MongoDB again
				locations.findByCoordinatesNear(p, d)
						.forEach(locationEventStream::broadcastNext);
			});
		});
	}

	public Handler retrieveLocation() {
		return ctx -> findLocation(ctx).then(ctx::render);
	}

	public Handler retrieveNearby() {
		return ctx -> findNearby(ctx, locations -> ctx.render(json(locations)));
	}

	private Promise<Location> findLocation(Context ctx) {
		return ctx.blocking(() -> locations.findOne(ctx.getPathTokens().get("id")));
	}

	private void findNearby(Context ctx, Action<? super Iterable<? extends Location>> action) {
		// have to resort to a callback here due to a Ratpack bug (can't execute nested promises)
		findLocation(ctx).then((l) -> action.execute(geoNear.findGeoNear(l)));
	}

}
