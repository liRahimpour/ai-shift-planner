package com.aishiftplanner.scheduler.availability.infrastructure;

import com.aishiftplanner.scheduler.availability.domain.EmployeeComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeCommentRepository extends JpaRepository<EmployeeComment, UUID> {

    Optional<EmployeeComment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<EmployeeComment> findAllByPlanningPeriodIdOrderByCreatedAtDesc(UUID planningPeriodId);

    List<EmployeeComment> findAllByPlanningPeriodIdAndEmployeeIdOrderByCreatedAtDesc(
            UUID planningPeriodId, UUID employeeId);
}
