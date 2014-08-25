package demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.domain.Location;
import demo.domain.LocationRepository;
import demo.geo.GeoNearService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.geo.GeoModule;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Component;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.util.MultiValueMap;
import reactor.core.Reactor;
import reactor.event.Event;
import reactor.util.StringUtils;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;
import static reactor.event.selector.Selectors.$;

/**
 * @author Jon Brisbin
 */
@Component
public class RestApi {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final LocationRepository locations;
	private final ObjectMapper       mapper;
	private final Reactor            eventBus;
	private final GeoNearService     geoNear;

	@Autowired
	public RestApi(LocationRepository locations,
	               ObjectMapper mapper,
	               Reactor eventBus,
	               GeoNearService geoNear) {
		this.locations = locations;
		// Configure Jackson to handle Spring Data geo.Point
		mapper.registerModule(new GeoModule());
		this.mapper = mapper;
		this.eventBus = eventBus;
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
			Location loc = locations.save(new Location().setCoordinates(new Point(0, 0)));

			eventBus.on($("location.save"), (Event<Location> ev) -> geoNear.addGeoNear(loc, ev.getData()));

			ctx.redirect(303, "http://localhost:5050/location/" + loc.getId());
		};
	}

	public Handler location() {
		return ctx -> {
			String id = ctx.getPathTokens().get("id");
			Location loc = locations.findOne(id);
			if (null == loc) {
				ctx.error(new IllegalArgumentException("Location with id " + id + " not found"));
				return;
			}

			ctx.byMethod(spec -> spec
					.get(c -> c.render(json(loc, mapper.writer())))
					.put(c -> {
						Location inLoc = c.parse(fromJson(Location.class, mapper));
						log.info("in: {}", inLoc);

						Location l = locations.save(loc.setAddress(inLoc.getAddress())
						                               .setCity(inLoc.getCity())
						                               .setProvince(inLoc.getProvince())
						                               .setPostalCode(inLoc.getPostalCode())
						                               .setCoordinates(inLoc.getCoordinates()));

						c.render(json(l, mapper.writer()));
					}));
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

			ctx.render(json(geoNear.getGeoNear(loc)));
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
