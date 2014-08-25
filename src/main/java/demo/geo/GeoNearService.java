package demo.geo;

import com.gs.collections.api.RichIterable;
import com.gs.collections.api.multimap.MutableMultimap;
import com.gs.collections.impl.multimap.set.UnifiedSetMultimap;
import demo.domain.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Service;
import reactor.core.Reactor;
import reactor.event.Event;

/**
 * @author Jon Brisbin
 */
@Service
public class GeoNearService implements ApplicationListener<AfterSaveEvent<Location>> {

	private final MutableMultimap<String, Location> nearbyCache = UnifiedSetMultimap.newMultimap();

	private final Reactor eventBus;

	@Autowired
	public GeoNearService(Reactor eventBus) {
		this.eventBus = eventBus;
	}

	@Override
	public void onApplicationEvent(AfterSaveEvent<Location> event) {
		eventBus.notify("location.save", Event.wrap(event.getSource()));
	}

	public void addGeoNear(Location l1, Location l2) {
		nearbyCache.put(l1.getId(), l2);
	}

	public RichIterable<Location> getGeoNear(Location loc) {
		return nearbyCache.get(loc.getId());
	}

}
