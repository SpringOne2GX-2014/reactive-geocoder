package demo.domain;

import org.springframework.data.repository.CrudRepository;

/**
 * @author Jon Brisbin
 */
public interface LocationRepository extends CrudRepository<Location, String> {
}
