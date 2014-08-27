package demo;

import demo.domain.Location;
import demo.domain.LocationRepository;
import demo.geo.GeoNearPredicate;
import demo.geo.GeoNearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.stereotype.Component;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import reactor.rx.Stream;
import reactor.rx.spec.Streams;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;

/**
 * @author Jon Brisbin
 */
@Component
public class ProcessorRestApi {

	private final LocationRepository locations;
	private final GeoNearService     geoNear;
	private final Stream<Location>   locationEventStream;
	private final ProcessorConfig    config;
	private final Distance           distance;

	@Autowired
	public ProcessorRestApi(LocationRepository locations,
	                        GeoNearService geoNear,
	                        Stream<Location> locationEventStream,
	                        ProcessorConfig config) {
		this.locations = locations;
		this.locationEventStream = locationEventStream;
		this.geoNear = geoNear;
		this.config = config;
		this.distance = new Distance(config.getDefaultDistance());
	}

	public Handler createLocation() {
		return ctx -> {
			// Save a new Location
			Location loc = locations.save(ctx.parse(fromJson(Location.class)));

			// Broadcast to others
			locationEventStream.broadcastNext(loc);

			// Only add Locations <= 10km away from my Location
			locationEventStream
					.merge(Streams.defer(locations.findAll())) // historical data
					.filter(l -> !loc.getId().equals(l.getId())) // not us
					.filter(new GeoNearPredicate(loc.getCoordinates(), distance)) // within 10km
					.consume(loc2 -> geoNear.addGeoNear(loc, loc2)); // add to cache

			// Redirect to REST URL
			ctx.redirect(303, config.getBaseUri() + "/location/" + loc.getId());
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
