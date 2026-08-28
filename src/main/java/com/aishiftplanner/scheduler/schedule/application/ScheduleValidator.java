package com.aishiftplanner.scheduler.schedule.application;

import com.aishiftplanner.scheduler.availability.domain.Availability;
import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import com.aishiftplanner.scheduler.availability.infrastructure.AvailabilityRepository;
import com.aishiftplanner.scheduler.employee.domain.Employee;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ValidationIssue;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ValidationResponse;
import com.aishiftplanner.scheduler.schedule.domain.Schedule;
import com.aishiftplanner.scheduler.schedule.domain.Shift;
import com.aishiftplanner.scheduler.schedule.domain.ShiftAssignment;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftAssignmentRepository;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftRepository;
import com.aishiftplanner.scheduler.shared.config.SchedulingProperties;
import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-checks a schedule after a manual edit and reports what it found.
 *
 * <p>Deliberately a <em>separate</em> implementation of the rules from the solver's constraint
 * streams, and that duplication is a considered trade-off. Re-running the solver to validate
 * a single drag-and-drop would take tens of seconds; a manager moving one person needs the
 * answer immediately. The risk is the two drifting apart, which is mitigated by both reading
 * their limits from the same {@link SchedulingProperties} and by the constraint tests pinning
 * the solver's behaviour precisely.
 *
 * <p>Note that this reports rather than blocks. A shift manager on the floor knows things the
 * system does not; the product's job is to make the consequence visible, not to overrule the
 * person accountable for the rota. Publishing is where the line is drawn.
 */
@Component
public class ScheduleValidator {

    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AvailabilityRepository availabilityRepository;
    private final SchedulingProperties properties;

    public ScheduleValidator(
            ShiftRepository shiftRepository,
            ShiftAssignmentRepository assignmentRepository,
            EmployeeRepository employeeRepository,
            AvailabilityRepository availabilityRepository,
            SchedulingProperties properties) {
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.availabilityRepository = availabilityRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ValidationResponse validate(Schedule schedule) {
        List<ShiftAssignment> assignments = assignmentRepository.findAllByScheduleId(schedule.getId());
        Map<UUID, Shift> shiftsById = new LinkedHashMap<>();
        for (Shift shift : shiftRepository.findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(
                schedule.getPlanningPeriodId())) {
            shiftsById.put(shift.getId(), shift);
        }

        Map<UUID, Employee> employeesById = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAllByOrganizationIdOrderByLastNameAscFirstNameAsc(
                schedule.getOrganizationId())) {
            employeesById.put(employee.getId(), employee);
        }

        Map<UUID, List<Availability>> availabilityByEmployee = new LinkedHashMap<>();
        for (Availability availability : availabilityRepository
                .findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(schedule.getPlanningPeriodId())) {
            availabilityByEmployee
                    .computeIfAbsent(availability.getEmployeeId(), k -> new ArrayList<>())
                    .add(availability);
        }

        List<ValidationIssue> issues = new ArrayList<>();

        // Group the filled assignments per employee so overlap, rest and hours can be checked
        // across everything that person is scheduled for, not just the seat that was edited.
        Map<UUID, List<ShiftAssignment>> byEmployee = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignments) {
            if (assignment.getEmployeeId() != null) {
                byEmployee.computeIfAbsent(assignment.getEmployeeId(), k -> new ArrayList<>())
                        .add(assignment);
            }
        }

        checkStaffingAndSkills(assignments, shiftsById, employeesById, issues);

        for (Map.Entry<UUID, List<ShiftAssignment>> entry : byEmployee.entrySet()) {
            Employee employee = employeesById.get(entry.getKey());
            if (employee == null) {
                continue;
            }
            checkAvailability(employee, entry.getValue(), shiftsById,
                    availabilityByEmployee.getOrDefault(employee.getId(), List.of()), issues);
            checkOverlapsAndRest(employee, entry.getValue(), shiftsById, issues);
            checkWeeklyHours(employee, entry.getValue(), shiftsById, issues);
        }

        boolean feasible = issues.stream().noneMatch(issue -> "HARD".equals(issue.severity()));
        return new ValidationResponse(feasible, issues);
    }

    private void checkStaffingAndSkills(
            List<ShiftAssignment> assignments,
            Map<UUID, Shift> shiftsById,
            Map<UUID, Employee> employeesById,
            List<ValidationIssue> issues) {

        Map<UUID, List<ShiftAssignment>> byShift = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignments) {
            byShift.computeIfAbsent(assignment.getShiftId(), k -> new ArrayList<>()).add(assignment);
        }

        for (Map.Entry<UUID, List<ShiftAssignment>> entry : byShift.entrySet()) {
            Shift shift = shiftsById.get(entry.getKey());
            if (shift == null) {
                continue;
            }
            long filled = entry.getValue().stream().filter(ShiftAssignment::isFilled).count();

            if (filled < shift.getMinimumEmployees()) {
                issues.add(ValidationIssue.hard(
                        "BELOW_MINIMUM_STAFFING",
                        "This shift has " + filled + " of at least " + shift.getMinimumEmployees()
                                + " required people.",
                        null,
                        shift.getId()));
            } else if (filled < shift.getRequiredEmployees()) {
                issues.add(ValidationIssue.warning(
                        "BELOW_TARGET_STAFFING",
                        "This shift has " + filled + " of the " + shift.getRequiredEmployees()
                                + " people planned for it.",
                        null,
                        shift.getId()));
            }

            for (Map.Entry<UUID, Integer> required : shift.getRequiredSkills().entrySet()) {
                long holders = entry.getValue().stream()
                        .filter(ShiftAssignment::isFilled)
                        .map(a -> employeesById.get(a.getEmployeeId()))
                        .filter(java.util.Objects::nonNull)
                        .filter(e -> e.getSkillIds().contains(required.getKey()))
                        .count();
                if (holders < required.getValue()) {
                    issues.add(ValidationIssue.hard(
                            "MISSING_REQUIRED_SKILL",
                            "This shift needs " + required.getValue()
                                    + " people with a required skill but has " + holders + ".",
                            null,
                            shift.getId()));
                }
            }
        }
    }

    private void checkAvailability(
            Employee employee,
            List<ShiftAssignment> assignments,
            Map<UUID, Shift> shiftsById,
            List<Availability> availabilities,
            List<ValidationIssue> issues) {

        for (ShiftAssignment assignment : assignments) {
            Shift shift = shiftsById.get(assignment.getShiftId());
            if (shift == null) {
                continue;
            }
            var onDate = availabilities.stream()
                    .filter(a -> a.getDate().equals(shift.getDate()))
                    .toList();

            var start = shift.getStartTime();
            var end = shift.isCrossesMidnight() ? com.aishiftplanner.scheduler.shared.domain.TimeWindow.END_OF_DAY : shift.getEndTime();

            if (onDate.isEmpty()) {
                issues.add(ValidationIssue.warning(
                        "NO_AVAILABILITY_SUBMITTED",
                        employee.fullName() + " did not submit availability for " + shift.getDate() + ".",
                        employee.getId(),
                        shift.getId()));
                continue;
            }
            boolean blocked = onDate.stream()
                    .anyMatch(a -> a.getAvailabilityType() == AvailabilityType.UNAVAILABLE
                            && a.overlaps(start, end));
            if (blocked) {
                issues.add(ValidationIssue.hard(
                        "EMPLOYEE_UNAVAILABLE",
                        employee.fullName() + " marked this time as unavailable.",
                        employee.getId(),
                        shift.getId()));
                continue;
            }
            boolean covered = onDate.stream()
                    .anyMatch(a -> a.getAvailabilityType() != AvailabilityType.UNAVAILABLE
                            && a.covers(start, end));
            if (!covered) {
                issues.add(ValidationIssue.warning(
                        "OUTSIDE_DECLARED_AVAILABILITY",
                        employee.fullName() + " is only partly available during this shift.",
                        employee.getId(),
                        shift.getId()));
            }
        }
    }

    private void checkOverlapsAndRest(
            Employee employee,
            List<ShiftAssignment> assignments,
            Map<UUID, Shift> shiftsById,
            List<ValidationIssue> issues) {

        List<TimeWindow> windows = new ArrayList<>();
        List<UUID> shiftIds = new ArrayList<>();
        for (ShiftAssignment assignment : assignments) {
            Shift shift = shiftsById.get(assignment.getShiftId());
            if (shift != null) {
                windows.add(shift.window());
                shiftIds.add(shift.getId());
            }
        }

        long minimumRestMinutes = (long) (properties.minimumRestHoursBetweenShifts() * 60);

        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                TimeWindow a = windows.get(i);
                TimeWindow b = windows.get(j);

                if (a.overlaps(b)) {
                    issues.add(ValidationIssue.hard(
                            "OVERLAPPING_SHIFTS",
                            employee.fullName() + " is scheduled for two overlapping shifts.",
                            employee.getId(),
                            shiftIds.get(j)));
                    continue;
                }
                TimeWindow earlier = a.start().isBefore(b.start()) ? a : b;
                TimeWindow later = earlier == a ? b : a;
                long restMinutes = Math.max(0, earlier.restBefore(later).toMinutes());

                if (restMinutes < minimumRestMinutes) {
                    // The message a manager actually needs to see after a manual edit:
                    // "Anna would only have 9 hours' rest before her next shift."
                    issues.add(ValidationIssue.warning(
                            "INSUFFICIENT_REST",
                            employee.fullName() + " would have only "
                                    + formatHours(Duration.ofMinutes(restMinutes))
                                    + " of rest before the next shift (minimum is "
                                    + properties.minimumRestHoursBetweenShifts() + " hours).",
                            employee.getId(),
                            shiftIds.get(j)));
                }
            }
        }
    }

    private void checkWeeklyHours(
            Employee employee,
            List<ShiftAssignment> assignments,
            Map<UUID, Shift> shiftsById,
            List<ValidationIssue> issues) {

        double totalHours = assignments.stream()
                .map(a -> shiftsById.get(a.getShiftId()))
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Shift::durationHours)
                .sum();

        double maximum = employee.getMaximumHoursPerWeek().doubleValue();
        if (maximum > 0 && totalHours > maximum) {
            issues.add(ValidationIssue.hard(
                    "EXCEEDS_MAXIMUM_HOURS",
                    employee.fullName() + " would work " + round(totalHours)
                            + " hours, above their maximum of " + round(maximum) + ".",
                    employee.getId(),
                    null));
        }

        double contract = employee.getContractHoursPerWeek().doubleValue();
        if (contract > 0 && totalHours > contract) {
            issues.add(ValidationIssue.warning(
                    "OVERTIME",
                    employee.fullName() + " would work " + round(totalHours - contract)
                            + " hours above their contract hours.",
                    employee.getId(),
                    null));
        }
    }

    private static String formatHours(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return minutes == 0 ? hours + " hours" : hours + "h " + minutes + "m";
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value * 10) / 10.0);
    }
}
