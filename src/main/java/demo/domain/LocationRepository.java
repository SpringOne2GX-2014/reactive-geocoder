package demo.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

/**
 * @author Jon Brisbin
 */
public interface LocationRepository extends PagingAndSortingRepository<Location, String> {

	Page<Location> findByCoordinatesNear(@Param("coords") Point coordinates,
	                                     @Param("distance") Distance distance,
	                                     Pageable pageable);

}
