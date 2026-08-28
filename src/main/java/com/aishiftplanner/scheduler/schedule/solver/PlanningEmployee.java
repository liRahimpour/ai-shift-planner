package com.aishiftplanner.scheduler.schedule.solver;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An employee as the solver sees them: an immutable problem fact.
 *
 * <p>Deliberately <em>not</em> the JPA {@code Employee} entity. Timefold copies and compares
 * these objects millions of times inside a solve, and handing it managed entities would drag
 * a Hibernate session, lazy proxies and dirty-checking into the hot loop — the classic way to
 * turn a 30-second solve into a 30-minute one. Keeping the solver's view as a plain record
 * also makes constraint tests trivial to write: no database, no context, just data.
 *
 * @param availabilityByDate per date, the windows the employee declared, already resolved
 * @param unavailableDates dates the employee blocked entirely
 * @param preferredWindows windows the employee actively wants (drives the soft constraints)
 */
public record PlanningEmployee(
        UUID id,
        String fullName,
        UUID homeLocationId,
        Set<UUID> clearedLocationIds,
        Set<UUID> skillIds,
        Set<UUID> departmentIds,
        BigDecimal hourlyWage,
        double contractHoursPerWeek,
        double minimumHoursPerWeek,
        double maximumHoursPerWeek,
        Map<java.time.LocalDate, Set<AvailabilitySlot>> availabilityByDate) {

    public boolean isClearedForLocation(UUID locationId) {
        return homeLocationId.equals(locationId) || clearedLocationIds.contains(locationId);
    }

    public boolean worksInDepartment(UUID departmentId) {
        return departmentIds.isEmpty() || departmentIds.contains(departmentId);
    }

    public boolean hasSkill(UUID skillId) {
        return skillIds.contains(skillId);
    }

    /** Cost of {@code hours} worked by this employee, at their plain hourly rate. */
    public BigDecimal costFor(double hours) {
        return hourlyWage.multiply(BigDecimal.valueOf(hours));
    }
}
