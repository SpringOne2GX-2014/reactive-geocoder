package demo;

import demo.domain.Location;
import demo.domain.LocationRepository;
import demo.geo.GeoNearService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Component;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.util.MultiValueMap;
import reactor.core.Reactor;
import reactor.event.Event;
import reactor.rx.Stream;
import reactor.util.StringUtils;

import java.util.function.Function;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;
import static reactor.event.selector.Selectors.$;

/**
 * @author Jon Brisbin
 */
@Component
public class ProcessorRestApi {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final LocationRepository                   locations;
	private final Reactor                              eventBus;
	private final GeoNearService                       geoNear;
	private final Function<Location, Stream<Location>> streamFactory;

	@Autowired
	public ProcessorRestApi(LocationRepository locations,
	                        Reactor eventBus,
	                        GeoNearService geoNear,
	                        Function<Location, Stream<Location>> streamFactory) {
		this.locations = locations;
		this.streamFactory = streamFactory;
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
			Location loc = locations.save(new Location().setCoordinates(new Point(-94.2741579, 37.496294)));

			Stream<Location> geoNearStream = streamFactory.apply(loc);
			log.info("stream: {}", geoNearStream);

			eventBus.on($("location.save"), (Event<Location> ev) -> {
				log.info("publishing: {}", ev);
				geoNearStream.broadcastNext(ev.getData());
			});

			locations.findAll().forEach(geoNearStream::broadcastNext);

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
					.get(c -> c.render(json(loc)))
					.put(c -> {
						Location inLoc = c.parse(fromJson(Location.class));
						log.info("in: {}", inLoc);

						Location l = locations.save(loc.setAddress(inLoc.getAddress())
						                               .setCity(inLoc.getCity())
						                               .setProvince(inLoc.getProvince())
						                               .setPostalCode(inLoc.getPostalCode())
						                               .setCoordinates(inLoc.getCoordinates()));

						c.render(json(l));
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
