package demo.geo;

import demo.domain.Location;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import reactor.function.Predicate;

/**
 * @author Jon Brisbin
 */
public class HaversinePredicate implements Predicate<Location> {

	private static final double EARTH_RADIUS_IN_METERS = 6372797.560856;

	private final Point    point;
	private final Distance distance;

	public HaversinePredicate(Point point, Distance distance) {
		this.point = point;
		this.distance = distance;
	}

	@Override
	public boolean test(Location location) {
		double lat1 = point.getX() / 1E6;
		double lat2 = location.getCoordinates().getX() / 1E6;
		double lon1 = point.getY() / 1E6;
		double lon2 = location.getCoordinates().getY() / 1E6;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
				Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		double c = 2 * Math.asin(Math.sqrt(a));

		return (EARTH_RADIUS_IN_METERS * c) <= distance.getValue();
	}

}
