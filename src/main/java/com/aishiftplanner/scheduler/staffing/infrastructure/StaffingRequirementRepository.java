package com.aishiftplanner.scheduler.staffing.infrastructure;

import com.aishiftplanner.scheduler.staffing.domain.StaffingRequirement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffingRequirementRepository extends JpaRepository<StaffingRequirement, UUID> {

    Optional<StaffingRequirement> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<StaffingRequirement> findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(UUID planningPeriodId);

    List<StaffingRequirement> findAllByPlanningPeriodIdAndDateOrderByStartTimeAsc(
            UUID planningPeriodId, LocalDate date);

    long countByPlanningPeriodId(UUID planningPeriodId);
}
