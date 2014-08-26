package demo;

import demo.domain.Location;
import demo.domain.LocationRepository;
import demo.geo.GeoNearPredicate;
import demo.geo.GeoNearService;
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
import reactor.rx.spec.Streams;
import reactor.util.StringUtils;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;

/**
 * @author Jon Brisbin
 */
@Component
public class ProcessorRestApi {

	private final Logger log = LoggerFactory.getLogger(getClass());

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

	public Handler root() {
		return ctx -> {
			// Support paging in the URL query string
			int page = pageNumber(ctx);

			Page<Location> p = locations.findAll(new PageRequest(page, config.getPageSize()));

			// Create next/prev Link headers for paging
			maybeAddPageLinks(ctx, p);

			ctx.render(json(p));
		};
	}

	public Handler createLocation() {
		return ctx -> {
			// Save a new Location
			Location loc = locations.save(ctx.parse(fromJson(Location.class)));

			// Broadcast to others
			locationEventStream.broadcastNext(loc);

			// Only add Locations <= 10km away from my Location
			locationEventStream
					.merge(Streams.defer(locations.findAll()))
					.filter(new GeoNearPredicate(loc.getCoordinates(), distance))
					.consume(loc2 -> geoNear.addGeoNear(loc, loc2));

			// Redirect to REST URL
			ctx.redirect(303, config.getBaseUri() + "/location/" + loc.getId());
		};
	}

	public Handler retrieveLocation() {
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

	public Handler retrieveNearby() {
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

	private int pageNumber(Context ctx) {
		MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
		int page = 0;
		if (params.containsKey("page")) {
			page = Integer.parseInt(params.get("page"));
		}
		return page;
	}

	private void maybeAddPageLinks(Context ctx, Page<?> page) {
		String baseUri = config.getBaseUri() + ctx.getRequest().getUri() + "?page=";

		StringBuffer linkHeader = new StringBuffer();
		if (page.hasPrevious()) {
			linkHeader.append("<").append(baseUri).append(page.getNumber() - 1).append(">; rel=\"previous\"");
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
