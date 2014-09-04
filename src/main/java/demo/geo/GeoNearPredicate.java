package demo.geo;

import demo.domain.Location;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import reactor.function.Predicate;

/**
 * This {@link reactor.function.Predicate} implementation calculates the distance between a "home" {@link
 * org.springframework.data.geo.Point} set at instantiation time to the {@link demo.domain.Location Locations} passing
 * through a {@link reactor.rx.Stream} using a Haversine formula [1]. If the distance is within the specified amount,
 * the {@link reactor.function.Predicate} allows the {@link demo.domain.Location} to pass through the {@link
 * reactor.rx.Stream}j to be processed. <p> [1] - http://rosettacode.org/wiki/Haversine_formula#Java </p>
 *
 * @author Jon Brisbin
 */
public class GeoNearPredicate implements Predicate<Location> {

	private static final double EARTH_RADIUS_IN_KM = 6372.797560856;

	private final Point    home;
	private final Distance distance;

	public GeoNearPredicate(Point home, Distance distance) {
		this.home = home;
		this.distance = distance;
	}

	/**
	 * Determine whether the given {@link demo.domain.Location} lines within the {@link
	 * org.springframework.data.geo.Distance} set at instantiation time.
	 *
	 * @param compareTo
	 * 		the location to calculate distance to from "home"
	 *
	 * @return {@literal true} if the location is within the specified distance, {@literal false} otherwise
	 */
	@Override
	public boolean test(Location compareTo) {
		double lat1 = home.getY();
		double lat2 = compareTo.getCoordinates()[1];
		double lon1 = home.getX();
		double lon2 = compareTo.getCoordinates()[0];

		if (lat1 == lat2 && lon1 == lon2) {
			return true;
		}

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

		return (EARTH_RADIUS_IN_KM * c) <= distance.getValue();
	}

}
