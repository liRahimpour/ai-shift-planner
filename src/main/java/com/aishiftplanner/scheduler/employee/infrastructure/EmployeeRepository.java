package com.aishiftplanner.scheduler.employee.infrastructure;

import com.aishiftplanner.scheduler.employee.domain.Employee;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Employee> findByUserId(UUID userId);

    List<Employee> findAllByOrganizationIdOrderByLastNameAscFirstNameAsc(UUID organizationId);

    List<Employee> findAllByOrganizationIdAndLocationIdOrderByLastNameAscFirstNameAsc(
            UUID organizationId, UUID locationId);

    List<Employee> findAllByOrganizationIdAndLocationIdAndActiveTrueOrderByLastNameAscFirstNameAsc(
            UUID organizationId, UUID locationId);

    /**
     * Loads the employees a planning run needs, with their id-set collections eagerly
     * fetched in one query each instead of N+1 lazy loads. The solver operates on a
     * fully-materialized working set, so paying for the joins once here is much cheaper
     * than a lazy load per employee inside the constraint evaluation loop.
     */
    @Query("""
            select distinct e from Employee e
            left join fetch e.skillIds
            where e.organizationId = :organizationId
              and e.active = true
              and (e.locationId = :locationId or :locationId member of e.additionalLocationIds)
            """)
    List<Employee> findSchedulableForLocation(
            @Param("organizationId") UUID organizationId, @Param("locationId") UUID locationId);

    long countByOrganizationIdAndLocationIdAndActiveTrue(UUID organizationId, UUID locationId);
}
