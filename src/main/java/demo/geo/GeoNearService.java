package demo.geo;

import com.google.common.collect.Iterables;
import com.gs.collections.impl.map.mutable.UnifiedMap;
import demo.domain.Location;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jon Brisbin
 */
@Service
public class GeoNearService {

	private final ConcurrentHashMap<String, Map<String, Location>> nearbyCache = new ConcurrentHashMap<>();

	public void addGeoNear(Location loc1, Location loc2) {
		Map<String, Location> locs = getNearby(loc1.getId());
		locs.put(loc2.getId(), loc2);
	}

	public Iterable<Location> findGeoNear(Location loc) {
		Map<String, Location> locs = getNearby(loc.getId());
		// Wrap the GS collection type because Jackson has problems rendering it
		return Iterables.unmodifiableIterable(locs.values());
	}

	public void clearGeoNear(Location loc) {
		nearbyCache.remove(loc.getId());
	}

	private Map<String, Location> getNearby(String id) {
		Map<String, Location> locs;
		if (null == (locs = nearbyCache.get(id))) {
			locs = nearbyCache.computeIfAbsent(id, s -> UnifiedMap.newMap());
		}
		return locs;
	}

}
