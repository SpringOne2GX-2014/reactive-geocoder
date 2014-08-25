package demo;

import demo.domain.Location;
import demo.domain.LocationRepository;
import demo.geo.GeoNearService;
import demo.geo.HaversinePredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.geo.Distance;
import org.springframework.stereotype.Component;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.util.MultiValueMap;
import reactor.rx.Stream;
import reactor.util.StringUtils;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;

/**
 * @author Jon Brisbin
 */
@Component
public class ProcessorRestApi {

	private final Logger   log      = LoggerFactory.getLogger(getClass());
	private final Distance distance = new Distance(10);

	private final LocationRepository locations;
	private final GeoNearService     geoNear;
	private final Stream<Location>   locationEventStream;

	@Autowired
	public ProcessorRestApi(LocationRepository locations,
	                        GeoNearService geoNear,
	                        Stream<Location> locationEventStream) {
		this.locations = locations;
		this.locationEventStream = locationEventStream;
		this.geoNear = geoNear;
	}

	public Handler root() {
		return ctx -> {
			// Support paging in the URL query string
			int page = pageNumber(ctx);

			Page<Location> p = locations.findAll(new PageRequest(page, 5));

			// Create next/prev Link headers for paging
			maybeAddPageLinks(ctx, p);

			ctx.render(json(p));
		};
	}

	public Handler postLocation() {
		return ctx -> {
			// Save a new Location
			Location loc = locations.save(ctx.parse(fromJson(Location.class)));

			// Only add Locations <= 10km away from my Location
			locationEventStream
					.filter(new HaversinePredicate(loc.getCoordinates(), distance))
					.consume(loc2 -> geoNear.maybeAddGeoNear(loc, loc2));

			locationEventStream.broadcastNext(loc);

			// Point to REST URL
			ctx.redirect(303, "http://localhost:5050/location/" + loc.getId());
		};
	}

	public Handler location() {
		return ctx -> {
			String id = ctx.getPathTokens().get("id");
			Location loc = locations.findOne(id);
			if (null == loc) {
				// We must have a real Location already
				ctx.error(new IllegalArgumentException("Location with id " + id + " not found"));
			} else {
				ctx.render(json(loc));
			}
		};
	}

	public Handler nearby() {
		return ctx -> {
			String id = ctx.getPathTokens().get("id");
			Location loc = locations.findOne(id);
			if (null == loc) {
				ctx.error(new IllegalArgumentException("Location with id " + id + " not found"));
				return;
			}

			ctx.render(json(geoNear.findGeoNear(loc)));
		};
	}

	private static int pageNumber(Context ctx) {
		MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
		int page = 0;
		if (params.containsKey("page")) {
			page = Integer.parseInt(params.get("page"));
		}
		return page;
	}

	private static void maybeAddPageLinks(Context ctx, Page<?> page) {
		StringBuffer linkHeader = new StringBuffer();
		if (page.hasPrevious()) {
			linkHeader.append("<http://localhost:5050/?page=").append(page.getNumber() - 1).append(">; rel=\"previous\"");
		}
		if (page.hasNext()) {
			if (StringUtils.hasText(linkHeader.toString())) {
				linkHeader.append(", ");
			}
			linkHeader.append("<http://localhost:5050/?page=").append(page.getNumber() + 1).append(">; rel=\"next\"");
		}
		if (StringUtils.hasText(linkHeader.toString())) {
			ctx.getResponse().getHeaders().set("Link", linkHeader.toString());
		}
	}

}
