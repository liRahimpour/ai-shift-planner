package com.aishiftplanner.scheduler.planning.infrastructure;

import com.aishiftplanner.scheduler.planning.domain.PlanningJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningJobRepository extends JpaRepository<PlanningJob, UUID> {

    Optional<PlanningJob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<PlanningJob> findAllByPlanningPeriodIdOrderByCreatedAtDesc(UUID planningPeriodId);

    /** The application-level half of the idempotency check; the database index is the other half. */
    Optional<PlanningJob> findFirstByPlanningPeriodIdAndStatusInOrderByCreatedAtDesc(
            UUID planningPeriodId, List<PlanningJob.Status> statuses);
}
