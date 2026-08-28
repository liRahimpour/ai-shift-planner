package com.aishiftplanner.scheduler.planning.infrastructure;

import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriodStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningPeriodRepository extends JpaRepository<PlanningPeriod, UUID> {

    Optional<PlanningPeriod> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<PlanningPeriod> findAllByOrganizationIdAndLocationIdOrderByStartDateDesc(
            UUID organizationId, UUID locationId);

    List<PlanningPeriod> findAllByOrganizationIdAndStatusOrderByStartDateAsc(
            UUID organizationId, PlanningPeriodStatus status);

    boolean existsByLocationIdAndStartDate(UUID locationId, LocalDate startDate);
}
