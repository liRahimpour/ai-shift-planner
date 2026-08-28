package com.aishiftplanner.scheduler.availability.infrastructure;

import com.aishiftplanner.scheduler.availability.domain.Availability;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    Optional<Availability> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Availability> findAllByPlanningPeriodIdAndEmployeeIdOrderByDateAscStartTimeAsc(
            UUID planningPeriodId, UUID employeeId);

    List<Availability> findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(UUID planningPeriodId);

    List<Availability> findAllByPlanningPeriodIdAndDate(UUID planningPeriodId, LocalDate date);

    void deleteAllByPlanningPeriodIdAndEmployeeId(UUID planningPeriodId, UUID employeeId);

    /**
     * How many distinct employees have submitted anything at all for this period.
     *
     * <p>Drives the manager dashboard's "31 of 34 employees have submitted" figure, which is
     * the number that tells a manager whether it is safe to start planning.
     */
    @Query("""
            select count(distinct a.employeeId) from Availability a
            where a.planningPeriodId = :planningPeriodId
            """)
    long countDistinctSubmittingEmployees(@Param("planningPeriodId") UUID planningPeriodId);

    @Query("""
            select distinct a.employeeId from Availability a
            where a.planningPeriodId = :planningPeriodId
            """)
    List<UUID> findEmployeeIdsWithSubmissions(@Param("planningPeriodId") UUID planningPeriodId);
}
