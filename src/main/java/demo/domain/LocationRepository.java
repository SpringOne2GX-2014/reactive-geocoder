package demo.domain;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * @author Jon Brisbin
 */
public interface LocationRepository extends CrudRepository<Location, String> {

	List<Location> findByCoordinatesNear(Point coordinates, Distance distance);

}
