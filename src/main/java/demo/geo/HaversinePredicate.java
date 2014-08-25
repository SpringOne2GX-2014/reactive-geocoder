package demo.geo;

import demo.domain.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import reactor.function.Predicate;

/**
 * @author Jon Brisbin
 */
public class HaversinePredicate implements Predicate<Location> {

	private static final double EARTH_RADIUS_IN_KM = 6372.797560856;

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Point    point;
	private final Distance distance;

	public HaversinePredicate(Point point, Distance distance) {
		this.point = point;
		this.distance = distance;
	}

	@Override
	public boolean test(Location location) {
		if (point.getX() == location.getCoordinates().getX()
				&& point.getY() == location.getCoordinates().getY()) {
			return false;
		}
		double lat1 = point.getX();
		double lat2 = location.getCoordinates().getX();
		double lon1 = point.getY();
		double lon2 = location.getCoordinates().getY();

		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		lat1 = Math.toRadians(lat1);
		lat2 = Math.toRadians(lat2);

		double a = Math.sin(dLat / 2)
				* Math.sin(dLat / 2)
				+ Math.sin(dLon / 2)
				* Math.sin(dLon / 2)
				* Math.cos(lat1)
				* Math.cos(lat2);
		double c = 2 * Math.asin(Math.sqrt(a));

		if (log.isInfoEnabled()) {
			log.info("dLat: {}, dLon: {}", dLat, dLon);
			log.info("c: {}", c);
			log.info("target distance: {}, p2p distance: {}", distance.getValue(), (EARTH_RADIUS_IN_KM * c));
		}

		return (EARTH_RADIUS_IN_KM * c) <= distance.getNormalizedValue();
	}

}
