package demo;

import demo.domain.Location;
import demo.domain.LocationRepository;
import demo.geo.GeoNearPredicate;
import demo.geo.GeoNearService;

import org.eclipse.jetty.jndi.local.localContextRoot;
import org.neo4j.cypher.internal.compiler.v2_1.docbuilders.logicalPlanDocBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Component;

import ratpack.handling.Context;
import ratpack.handling.Handler;
import reactor.core.Reactor;
import reactor.event.Event;
import reactor.rx.Stream;
import reactor.rx.spec.Streams;
import reactor.tuple.Tuple;
import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;
import static reactor.event.selector.Selectors.$;

/**
 * @author Jon Brisbin
 */
@Component
public class ProcessorRestApi {

	private final LocationRepository locations;
	private final GeoNearService     geoNear;
	private final Reactor            eventBus;
	private final Stream<Location>   locationEventStream;
	private final ProcessorConfig    config;
	private final Distance           defaultDistance;

	@Autowired
	public ProcessorRestApi(LocationRepository locations,
	                        GeoNearService geoNear,
	                        Reactor eventBus,
	                        Stream<Location> locationEventStream,
	                        ProcessorConfig config) {
		this.locations = locations;
		this.geoNear = geoNear;
		this.eventBus = eventBus;
		this.locationEventStream = locationEventStream;
		this.config = config;
		this.defaultDistance = new Distance(config.getDefaultDistance());
	}

	public Handler createLocation() {
		return ctx -> {
			// Save a new Location
			Location loc = locations.save(ctx.parse(fromJson(Location.class)));

			// Broadcast to others
			locationEventStream.broadcastNext(loc);

			// Only add Locations <= 10km away from my Location
			GeoNearPredicate filter = new GeoNearPredicate(new Point(loc.getCoordinates()[0], loc.getCoordinates()[1]),
			                                               defaultDistance);

			Streams.<Location>merge(locationEventStream, Streams.<Location>defer(locations.findAll()))
			//.filter(l -> !loc.getId().equals(l.getId())) // not us
					.filter(filter)
					.consume(loc2 -> geoNear.addGeoNear(loc, loc2)); // add to cache

			// Listen for changes to distance value
			eventBus.on($(loc.getId() + ".distance"), filter);

			// Redirect to REST URL
			ctx.redirect(303, config.getBaseUri() + "/location/" + loc.getId());
		};
	}

	public Handler updateLocation() {
		return ctx -> {
			Location loc;
			if (null != (loc = findLocation(ctx))) {
				// Update Location
				Location inLoc = ctx.parse(fromJson(Location.class));
				loc.setName(inLoc.getName())
				   .setAddress(inLoc.getAddress())
				   .setCity(inLoc.getCity())
				   .setProvince(inLoc.getProvince())
				   .setPostalCode(inLoc.getPostalCode())
				   .setCoordinates(inLoc.getCoordinates());
				loc = locations.save(loc);

				// Update distance
				int distance = Integer.parseInt(ctx.getRequest()
				                                   .getQueryParams()
				                                   .get("distance"));
				Point p = new Point(loc.getCoordinates()[0], loc.getCoordinates()[1]);
				Distance d = new Distance(distance);
				GeoNearPredicate filter = new GeoNearPredicate(p, d);

				// Notify Predicate of the change
				eventBus.notify(loc.getId() + ".distance", Event.wrap(Tuple.of(p, d)));

				// Clear cache
				geoNear.clearGeoNear(loc);

				// Find nearby by querying MongoDB again
				Location loc1 = loc;
				locations.findByCoordinatesNear(p, d)
				         .forEach(loc2 -> {
					         if (!loc1.getId().equals(loc2.getId()) && filter.test(loc2)) {
						         geoNear.addGeoNear(loc1, loc2);
					         }
				         });
			}
		};
	}

	public Handler retrieveLocation() {
		return ctx -> {
			Location loc;
			if (null != (loc = findLocation(ctx))) {
				ctx.render(json(loc));
			}
		};
	}

	public Handler retrieveNearby() {
		return ctx -> {
			Location loc;
			if (null != (loc = findLocation(ctx))) {
				ctx.render(json(geoNear.findGeoNear(loc)));
			}
		};
	}

	private Location findLocation(Context ctx) {
		String id = ctx.getPathTokens().get("id");
		Location loc = locations.findOne(id);
		if (null == loc) {
			// We must have a real Location already
			ctx.error(new IllegalArgumentException("Location with id " + id + " not found"));
		}
		return loc;
	}

}
