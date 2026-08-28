package com.aishiftplanner.scheduler.schedule.application;

import com.aishiftplanner.scheduler.availability.domain.Availability;
import com.aishiftplanner.scheduler.availability.infrastructure.AvailabilityRepository;
import com.aishiftplanner.scheduler.employee.domain.Employee;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.schedule.domain.Shift;
import com.aishiftplanner.scheduler.schedule.domain.ShiftAssignment;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftRepository;
import com.aishiftplanner.scheduler.schedule.solver.AvailabilitySlot;
import com.aishiftplanner.scheduler.schedule.solver.ConstraintWeights;
import com.aishiftplanner.scheduler.schedule.solver.PlanningEmployee;
import com.aishiftplanner.scheduler.schedule.solver.PlanningShift;
import com.aishiftplanner.scheduler.schedule.solver.ShiftSchedule;
import com.aishiftplanner.scheduler.schedule.solver.ShiftSlot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns database rows into the immutable object graph the solver works on.
 *
 * <p>This is the seam that keeps JPA out of the solver. Everything is read once, eagerly,
 * inside one read-only transaction and converted to plain records; from then on the solve
 * touches no database at all. Doing it any other way — passing managed entities in, or
 * letting a lazy collection be traversed during scoring — turns a CPU-bound solve into a
 * database-bound one and makes the run duration depend on connection pool contention.
 */
@Component
public class PlanningProblemLoader {

    private final EmployeeRepository employeeRepository;
    private final AvailabilityRepository availabilityRepository;
    private final ShiftRepository shiftRepository;

    public PlanningProblemLoader(
            EmployeeRepository employeeRepository,
            AvailabilityRepository availabilityRepository,
            ShiftRepository shiftRepository) {
        this.employeeRepository = employeeRepository;
        this.availabilityRepository = availabilityRepository;
        this.shiftRepository = shiftRepository;
    }

    /**
     * Builds an unsolved problem for one strategy.
     *
     * @param pinnedAssignments existing assignments a manager has pinned; these seats are
     *     pre-filled and excluded from the search
     */
    @Transactional(readOnly = true)
    public ShiftSchedule load(
            PlanningPeriod period, ConstraintWeights weights, List<ShiftAssignment> pinnedAssignments) {

        List<Employee> employees = employeeRepository.findSchedulableForLocation(
                period.getOrganizationId(), period.getLocationId());
        List<Shift> shifts = shiftRepository.findAllForPlanning(period.getId());
        List<Availability> availabilities =
                availabilityRepository.findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(period.getId());

        Map<UUID, Map<LocalDate, Set<AvailabilitySlot>>> availabilityByEmployee =
                groupAvailability(availabilities);

        Map<UUID, PlanningEmployee> planningEmployees = new LinkedHashMap<>();
        for (Employee employee : employees) {
            planningEmployees.put(
                    employee.getId(),
                    toPlanningEmployee(
                            employee,
                            availabilityByEmployee.getOrDefault(employee.getId(), Map.of())));
        }

        // shiftId → (slotIndex → pinned employee), so pinned seats keep both their occupant
        // and their position rather than being re-derived from an arbitrary ordering.
        Map<UUID, Map<Integer, UUID>> pinnedByShift = new LinkedHashMap<>();
        for (ShiftAssignment assignment : pinnedAssignments) {
            if (assignment.getEmployeeId() != null) {
                pinnedByShift
                        .computeIfAbsent(assignment.getShiftId(), k -> new LinkedHashMap<>())
                        .put(assignment.getSlotIndex(), assignment.getEmployeeId());
            }
        }

        List<ShiftSlot> slots = new ArrayList<>();
        for (Shift shift : shifts) {
            PlanningShift planningShift = PlanningShift.from(shift);
            Map<Integer, UUID> pinnedForShift = pinnedByShift.getOrDefault(shift.getId(), Map.of());

            for (int index = 0; index < shift.getRequiredEmployees(); index++) {
                ShiftSlot slot = new ShiftSlot(UUID.randomUUID(), planningShift, index);
                UUID pinnedEmployeeId = pinnedForShift.get(index);
                if (pinnedEmployeeId != null) {
                    PlanningEmployee pinnedEmployee = planningEmployees.get(pinnedEmployeeId);
                    if (pinnedEmployee != null) {
                        slot.setEmployee(pinnedEmployee);
                        slot.setPinned(true);
                    }
                    // If the pinned employee is no longer schedulable (deactivated, moved
                    // location), the pin is dropped rather than pinning a stale value: the
                    // seat becomes free and shows up as unfilled, which is visible and
                    // fixable, instead of silently violating a hard constraint.
                }
                slots.add(slot);
            }
        }

        return new ShiftSchedule(
                List.copyOf(planningEmployees.values()),
                slots,
                weights,
                period.getStartDate(),
                period.getEndDate());
    }

    private Map<UUID, Map<LocalDate, Set<AvailabilitySlot>>> groupAvailability(
            List<Availability> availabilities) {
        Map<UUID, Map<LocalDate, Set<AvailabilitySlot>>> result = new LinkedHashMap<>();
        for (Availability availability : availabilities) {
            result.computeIfAbsent(availability.getEmployeeId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(availability.getDate(), k -> new LinkedHashSet<>())
                    .add(new AvailabilitySlot(
                            availability.getAvailabilityType(),
                            availability.getStartTime(),
                            availability.getEndTime()));
        }
        return result;
    }

    private PlanningEmployee toPlanningEmployee(
            Employee employee, Map<LocalDate, Set<AvailabilitySlot>> availability) {
        return new PlanningEmployee(
                employee.getId(),
                employee.fullName(),
                employee.getLocationId(),
                Set.copyOf(employee.getAdditionalLocationIds()),
                Set.copyOf(employee.getSkillIds()),
                Set.copyOf(employee.getDepartmentIds()),
                employee.getHourlyWage(),
                employee.getContractHoursPerWeek().doubleValue(),
                employee.getMinimumHoursPerWeek().doubleValue(),
                employee.getMaximumHoursPerWeek().doubleValue(),
                Map.copyOf(availability));
    }
}
