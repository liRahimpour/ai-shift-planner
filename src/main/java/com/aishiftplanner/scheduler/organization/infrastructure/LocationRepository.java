package com.aishiftplanner.scheduler.organization.infrastructure;

import com.aishiftplanner.scheduler.organization.domain.Location;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder is scoped by {@code organizationId}.
 *
 * <p>There is intentionally no plain {@code findById(UUID)} in the service layer's
 * vocabulary: looking a row up by primary key alone is how cross-tenant leaks happen, since
 * a valid id from another organization would resolve successfully. Callers use
 * {@link #findByIdAndOrganizationId} so that tenant isolation is structural rather than a
 * check someone has to remember to write.
 */
public interface LocationRepository extends JpaRepository<Location, UUID> {

    Optional<Location> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Location> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    List<Location> findAllByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}
