package com.aishiftplanner.scheduler.schedule.infrastructure;

import com.aishiftplanner.scheduler.schedule.domain.PlanningStrategy;
import com.aishiftplanner.scheduler.schedule.domain.Schedule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    Optional<Schedule> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Schedule> findAllByPlanningPeriodIdOrderByStrategyAsc(UUID planningPeriodId);

    Optional<Schedule> findByPlanningPeriodIdAndSelectedTrue(UUID planningPeriodId);

    /** Tenant-scoped variant used at trust boundaries such as the schedule chat. */
    Optional<Schedule> findByPlanningPeriodIdAndOrganizationIdAndSelectedTrue(
            UUID planningPeriodId, UUID organizationId);

    Optional<Schedule> findByPlanningPeriodIdAndStrategy(UUID planningPeriodId, PlanningStrategy strategy);

    void deleteAllByPlanningPeriodId(UUID planningPeriodId);
}
