package com.aishiftplanner.scheduler.schedule.infrastructure;

import com.aishiftplanner.scheduler.schedule.domain.Shift;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    Optional<Shift> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Shift> findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(UUID planningPeriodId);

    List<Shift> findAllByPlanningPeriodIdAndDateOrderByStartTimeAsc(UUID planningPeriodId, LocalDate date);

    void deleteAllByPlanningPeriodId(UUID planningPeriodId);

    /**
     * Loads the shifts for a planning run with their required-skill map already fetched.
     *
     * <p>The solver evaluates skill constraints inside its inner loop; a lazy load per shift
     * there would turn a 30-second solve into a database-bound crawl.
     */
    @Query("""
            select distinct s from Shift s
            left join fetch s.requiredSkills
            where s.planningPeriodId = :planningPeriodId
            """)
    List<Shift> findAllForPlanning(@Param("planningPeriodId") UUID planningPeriodId);
}
