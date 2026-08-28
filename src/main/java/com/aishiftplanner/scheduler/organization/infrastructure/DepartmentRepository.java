package com.aishiftplanner.scheduler.organization.infrastructure;

import com.aishiftplanner.scheduler.organization.domain.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Department> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    List<Department> findAllByLocationIdAndOrganizationIdOrderByNameAsc(UUID locationId, UUID organizationId);

    List<Department> findAllByLocationIdAndOrganizationIdAndActiveTrueOrderByNameAsc(
            UUID locationId, UUID organizationId);

    boolean existsByLocationIdAndName(UUID locationId, String name);
}
