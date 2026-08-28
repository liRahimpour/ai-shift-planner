package com.aishiftplanner.scheduler.chat.application;

import com.aishiftplanner.scheduler.availability.domain.Availability;
import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import com.aishiftplanner.scheduler.availability.infrastructure.AvailabilityRepository;
import com.aishiftplanner.scheduler.employee.domain.Employee;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import com.aishiftplanner.scheduler.organization.infrastructure.DepartmentRepository;
import com.aishiftplanner.scheduler.schedule.domain.Schedule;
import com.aishiftplanner.scheduler.schedule.domain.Shift;
import com.aishiftplanner.scheduler.schedule.domain.ShiftAssignment;
import com.aishiftplanner.scheduler.schedule.infrastructure.ScheduleRepository;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftAssignmentRepository;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftRepository;
import com.aishiftplanner.scheduler.shared.config.SchedulingProperties;
import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only queries over the current schedule, expressed as plain data.
 *
 * <p>This is the layer the chat tools sit on, and it is where "the database is the source of
 * truth" is actually enforced. Every number a chat answer contains originates from one of
 * these methods; the model's only job is to phrase the result. Nothing here consults the
 * model, and nothing here returns entities — only records that are safe to serialize.
 */
@Service
public class ScheduleQueryService {

    public record AssignedEmployee(UUID employeeId, String name, String department, String from, String to) {
    }

    public record ShiftCoverage(
            UUID shiftId,
            String department,
            LocalDate date,
            String from,
            String to,
            int required,
            int minimum,
            int filled,
            List<AssignedEmployee> people) {
    }

    public record EmployeeHours(
            UUID employeeId,
            String name,
            double scheduledHours,
            double contractHours,
            double differenceHours,
            int shiftCount) {
    }

    public record ReplacementCandidate(
            UUID employeeId,
            String name,
            boolean availableAtThatTime,
            boolean hasRequiredSkills,
            double hoursAfterTakingShift,
            double contractHours,
            boolean wouldBeOvertime,
            boolean hasEnoughRest,
            boolean conflictsWithAWish,
            BigDecimal estimatedCost,
            int rank,
            String reason) {
    }

    private final ScheduleRepository scheduleRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AvailabilityRepository availabilityRepository;
    private final SchedulingProperties schedulingProperties;

    public ScheduleQueryService(
            ScheduleRepository scheduleRepository,
            ShiftRepository shiftRepository,
            ShiftAssignmentRepository assignmentRepository,
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            AvailabilityRepository availabilityRepository,
            SchedulingProperties schedulingProperties) {
        this.scheduleRepository = scheduleRepository;
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.availabilityRepository = availabilityRepository;
        this.schedulingProperties = schedulingProperties;
    }

    /** Who works on a given date, optionally filtered by department name. */
    @Transactional(readOnly = true)
    public List<ShiftCoverage> scheduleForDate(UUID planningPeriodId, LocalDate date, String departmentName) {
        Schedule schedule = requireSelectedSchedule(planningPeriodId);
        Map<UUID, String> departments = departmentNames(schedule.getOrganizationId());
        Map<UUID, String> employees = employeeNames(schedule.getOrganizationId());
        Map<UUID, List<ShiftAssignment>> byShift = assignmentsByShift(schedule.getId());

        return shiftRepository.findAllByPlanningPeriodIdAndDateOrderByStartTimeAsc(planningPeriodId, date)
                .stream()
                .filter(shift -> departmentName == null
                        || departmentName.isBlank()
                        || departmentName.equalsIgnoreCase(departments.get(shift.getDepartmentId())))
                .map(shift -> toCoverage(shift, byShift, departments, employees))
                .toList();
    }

    /** Shifts that are below their minimum staffing — the manager's "what still hurts" list. */
    @Transactional(readOnly = true)
    public List<ShiftCoverage> understaffedShifts(UUID planningPeriodId) {
        Schedule schedule = requireSelectedSchedule(planningPeriodId);
        Map<UUID, String> departments = departmentNames(schedule.getOrganizationId());
        Map<UUID, String> employees = employeeNames(schedule.getOrganizationId());
        Map<UUID, List<ShiftAssignment>> byShift = assignmentsByShift(schedule.getId());

        return shiftRepository.findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(planningPeriodId).stream()
                .map(shift -> toCoverage(shift, byShift, departments, employees))
                .filter(coverage -> coverage.filled() < coverage.minimum())
                .toList();
    }

    /** Hours per employee against their contract, sorted by the largest surplus first. */
    @Transactional(readOnly = true)
    public List<EmployeeHours> employeeHours(UUID planningPeriodId, UUID onlyEmployeeId) {
        Schedule schedule = requireSelectedSchedule(planningPeriodId);
        Map<UUID, Shift> shifts = shiftsById(planningPeriodId);

        Map<UUID, Double> hours = new LinkedHashMap<>();
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignmentRepository.findAllByScheduleId(schedule.getId())) {
            if (assignment.getEmployeeId() == null) {
                continue;
            }
            Shift shift = shifts.get(assignment.getShiftId());
            if (shift == null) {
                continue;
            }
            hours.merge(assignment.getEmployeeId(), shift.durationHours(), Double::sum);
            counts.merge(assignment.getEmployeeId(), 1, Integer::sum);
        }

        return employeeRepository
                .findAllByOrganizationIdOrderByLastNameAscFirstNameAsc(schedule.getOrganizationId())
                .stream()
                .filter(employee -> onlyEmployeeId == null || onlyEmployeeId.equals(employee.getId()))
                .map(employee -> {
                    double scheduled = hours.getOrDefault(employee.getId(), 0.0);
                    double contract = employee.getContractHoursPerWeek().doubleValue();
                    return new EmployeeHours(
                            employee.getId(),
                            employee.fullName(),
                            round(scheduled),
                            round(contract),
                            round(scheduled - contract),
                            counts.getOrDefault(employee.getId(), 0));
                })
                .sorted(Comparator.comparingDouble(EmployeeHours::differenceHours).reversed())
                .toList();
    }

    /**
     * Ranked replacements for someone who has called in sick.
     *
     * <p>The ranking is deterministic and computed here, not by the model. That matters twice
     * over: the manager gets the same answer every time they ask, and every factor behind a
     * ranking ("available: yes, BAR skill: yes, 31 hours after this, no overtime") is a real
     * value from the database that can be checked, rather than a plausible-sounding sentence.
     */
    @Transactional(readOnly = true)
    public List<ReplacementCandidate> findReplacements(UUID planningPeriodId, UUID shiftId) {
        Schedule schedule = requireSelectedSchedule(planningPeriodId);
        Shift shift = shiftRepository
                .findByIdAndOrganizationId(shiftId, schedule.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown shift"));

        TimeWindow window = shift.window();
        Map<UUID, Shift> shifts = shiftsById(planningPeriodId);
        List<ShiftAssignment> assignments = assignmentRepository.findAllByScheduleId(schedule.getId());

        Map<UUID, Double> hoursByEmployee = new LinkedHashMap<>();
        Map<UUID, List<TimeWindow>> windowsByEmployee = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignments) {
            if (assignment.getEmployeeId() == null) {
                continue;
            }
            Shift assigned = shifts.get(assignment.getShiftId());
            if (assigned == null) {
                continue;
            }
            hoursByEmployee.merge(assignment.getEmployeeId(), assigned.durationHours(), Double::sum);
            windowsByEmployee
                    .computeIfAbsent(assignment.getEmployeeId(), k -> new ArrayList<>())
                    .add(assigned.window());
        }

        List<UUID> alreadyOnThisShift = assignments.stream()
                .filter(a -> a.getShiftId().equals(shiftId) && a.getEmployeeId() != null)
                .map(ShiftAssignment::getEmployeeId)
                .toList();

        Map<UUID, List<Availability>> availability = availabilityByEmployee(planningPeriodId);
        double minimumRestHours = schedulingProperties.minimumRestHoursBetweenShifts();

        List<ReplacementCandidate> candidates = new ArrayList<>();
        for (Employee employee : employeeRepository.findSchedulableForLocation(
                schedule.getOrganizationId(), shift.getLocationId())) {

            if (alreadyOnThisShift.contains(employee.getId())) {
                continue;
            }
            List<TimeWindow> existing = windowsByEmployee.getOrDefault(employee.getId(), List.of());
            if (existing.stream().anyMatch(w -> w.overlaps(window))) {
                // Already working then; not a candidate at all rather than a poorly ranked one.
                continue;
            }

            List<Availability> declared = availability.getOrDefault(employee.getId(), List.of()).stream()
                    .filter(a -> a.getDate().equals(shift.getDate()))
                    .toList();
            var start = shift.getStartTime();
            var end = shift.isCrossesMidnight() ? com.aishiftplanner.scheduler.shared.domain.TimeWindow.END_OF_DAY : shift.getEndTime();

            boolean blocked = declared.stream()
                    .anyMatch(a -> a.getAvailabilityType() == AvailabilityType.UNAVAILABLE
                            && a.overlaps(start, end));
            if (blocked) {
                continue;
            }
            boolean available = declared.stream()
                    .anyMatch(a -> a.getAvailabilityType() != AvailabilityType.UNAVAILABLE
                            && a.covers(start, end));
            boolean hasSkills = employee.hasAllSkills(shift.getRequiredSkills().keySet());

            double currentHours = hoursByEmployee.getOrDefault(employee.getId(), 0.0);
            double hoursAfter = currentHours + shift.durationHours();
            double contract = employee.getContractHoursPerWeek().doubleValue();
            boolean overtime = contract > 0 && hoursAfter > contract;
            boolean overMaximum = hoursAfter > employee.getMaximumHoursPerWeek().doubleValue();

            boolean enoughRest = existing.stream().allMatch(other -> {
                TimeWindow earlier = other.start().isBefore(window.start()) ? other : window;
                TimeWindow later = earlier == other ? window : other;
                return earlier.restBefore(later).toMinutes() >= minimumRestHours * 60;
            });

            boolean wishConflict = declared.stream()
                    .anyMatch(a -> a.getAvailabilityType() == AvailabilityType.PREFERRED
                            && !a.covers(start, end));

            candidates.add(new ReplacementCandidate(
                    employee.getId(),
                    employee.fullName(),
                    available,
                    hasSkills,
                    round(hoursAfter),
                    round(contract),
                    overtime,
                    enoughRest,
                    wishConflict,
                    employee.getHourlyWage()
                            .multiply(BigDecimal.valueOf(shift.durationHours()))
                            .setScale(2, RoundingMode.HALF_UP),
                    0,
                    buildReason(available, hasSkills, enoughRest, overtime, overMaximum)));
        }

        // Ranking order encodes what a shift manager actually cares about, most important
        // first: can they legally and practically do it, are they qualified, is it fair to
        // them, and only then what it costs.
        List<ReplacementCandidate> ranked = candidates.stream()
                .sorted(Comparator
                        .comparing(ReplacementCandidate::hasEnoughRest).reversed()
                        .thenComparing(Comparator.comparing(ReplacementCandidate::hasRequiredSkills).reversed())
                        .thenComparing(Comparator.comparing(ReplacementCandidate::availableAtThatTime).reversed())
                        .thenComparing(ReplacementCandidate::wouldBeOvertime)
                        .thenComparing(ReplacementCandidate::conflictsWithAWish)
                        .thenComparing(ReplacementCandidate::hoursAfterTakingShift)
                        .thenComparing(ReplacementCandidate::estimatedCost))
                .toList();

        List<ReplacementCandidate> withRank = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            ReplacementCandidate candidate = ranked.get(i);
            withRank.add(new ReplacementCandidate(
                    candidate.employeeId(),
                    candidate.name(),
                    candidate.availableAtThatTime(),
                    candidate.hasRequiredSkills(),
                    candidate.hoursAfterTakingShift(),
                    candidate.contractHours(),
                    candidate.wouldBeOvertime(),
                    candidate.hasEnoughRest(),
                    candidate.conflictsWithAWish(),
                    candidate.estimatedCost(),
                    i + 1,
                    candidate.reason()));
        }
        return withRank;
    }

    /** Who asked to be free on a given date. */
    @Transactional(readOnly = true)
    public List<String> employeesWhoRequestedOff(UUID planningPeriodId, LocalDate date) {
        Schedule schedule = requireSelectedSchedule(planningPeriodId);
        Map<UUID, String> names = employeeNames(schedule.getOrganizationId());

        return availabilityRepository.findAllByPlanningPeriodIdAndDate(planningPeriodId, date).stream()
                .filter(a -> a.getAvailabilityType() == AvailabilityType.UNAVAILABLE)
                .map(a -> names.get(a.getEmployeeId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The facts behind one person's assignment, for the "why does Sarah work Saturday?"
     * question.
     *
     * <p>Returns evidence, not narrative. The model turns these into a sentence; it does not
     * get to invent a reason that sounds better than the real one.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> explainAssignment(UUID planningPeriodId, UUID shiftId, UUID employeeId) {
        Schedule schedule = requireSelectedSchedule(planningPeriodId);
        Shift shift = shiftRepository
                .findByIdAndOrganizationId(shiftId, schedule.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown shift"));
        Employee employee = employeeRepository
                .findByIdAndOrganizationId(employeeId, schedule.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown employee"));

        List<Availability> declared = availabilityRepository
                .findAllByPlanningPeriodIdAndEmployeeIdOrderByDateAscStartTimeAsc(planningPeriodId, employeeId)
                .stream()
                .filter(a -> a.getDate().equals(shift.getDate()))
                .toList();

        EmployeeHours hours = employeeHours(planningPeriodId, employeeId).stream()
                .findFirst()
                .orElse(new EmployeeHours(employeeId, employee.fullName(), 0, 0, 0, 0));

        List<String> unavailableColleagues = employeesWhoRequestedOff(planningPeriodId, shift.getDate());

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("employee", employee.fullName());
        evidence.put("date", shift.getDate().toString());
        evidence.put("shift", shift.getStartTime() + "-" + shift.getEndTime());
        evidence.put("declaredAvailability", declared.stream()
                .map(a -> a.getAvailabilityType() + " "
                        + (a.isWholeDay() ? "whole day" : a.getStartTime() + "-" + a.getEndTime()))
                .toList());
        evidence.put("holdsRequiredSkills", employee.hasAllSkills(shift.getRequiredSkills().keySet()));
        evidence.put("scheduledHours", hours.scheduledHours());
        evidence.put("contractHours", hours.contractHours());
        evidence.put("colleaguesUnavailableThatDay", unavailableColleagues);
        return evidence;
    }

    // --- internals -----------------------------------------------------------

    private Schedule requireSelectedSchedule(UUID planningPeriodId) {
        return scheduleRepository
                .findByPlanningPeriodIdAndSelectedTrue(planningPeriodId)
                .orElseThrow(() -> new IllegalStateException(
                        "No schedule has been selected for this planning period yet."));
    }

    private ShiftCoverage toCoverage(
            Shift shift,
            Map<UUID, List<ShiftAssignment>> byShift,
            Map<UUID, String> departments,
            Map<UUID, String> employees) {

        List<ShiftAssignment> assignments = byShift.getOrDefault(shift.getId(), List.of());
        List<AssignedEmployee> people = assignments.stream()
                .filter(ShiftAssignment::isFilled)
                .map(a -> new AssignedEmployee(
                        a.getEmployeeId(),
                        employees.get(a.getEmployeeId()),
                        departments.get(shift.getDepartmentId()),
                        shift.getStartTime().toString(),
                        shift.getEndTime().toString()))
                .toList();

        return new ShiftCoverage(
                shift.getId(),
                departments.get(shift.getDepartmentId()),
                shift.getDate(),
                shift.getStartTime().toString(),
                shift.getEndTime().toString(),
                shift.getRequiredEmployees(),
                shift.getMinimumEmployees(),
                people.size(),
                people);
    }

    private Map<UUID, List<ShiftAssignment>> assignmentsByShift(UUID scheduleId) {
        Map<UUID, List<ShiftAssignment>> byShift = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignmentRepository.findAllByScheduleId(scheduleId)) {
            byShift.computeIfAbsent(assignment.getShiftId(), k -> new ArrayList<>()).add(assignment);
        }
        return byShift;
    }

    private Map<UUID, Shift> shiftsById(UUID planningPeriodId) {
        Map<UUID, Shift> shifts = new LinkedHashMap<>();
        shiftRepository.findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(planningPeriodId)
                .forEach(shift -> shifts.put(shift.getId(), shift));
        return shifts;
    }

    private Map<UUID, List<Availability>> availabilityByEmployee(UUID planningPeriodId) {
        Map<UUID, List<Availability>> result = new LinkedHashMap<>();
        for (Availability availability : availabilityRepository
                .findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(planningPeriodId)) {
            result.computeIfAbsent(availability.getEmployeeId(), k -> new ArrayList<>()).add(availability);
        }
        return result;
    }

    private Map<UUID, String> employeeNames(UUID organizationId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        employeeRepository.findAllByOrganizationIdOrderByLastNameAscFirstNameAsc(organizationId)
                .forEach(e -> names.put(e.getId(), e.fullName()));
        return names;
    }

    private Map<UUID, String> departmentNames(UUID organizationId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        departmentRepository.findAllByOrganizationIdOrderByNameAsc(organizationId)
                .forEach(d -> names.put(d.getId(), d.getName()));
        return names;
    }

    /** Finds a department id by (case-insensitive) name — chat users type "Bar", not a UUID. */
    @Transactional(readOnly = true)
    public Optional<UUID> departmentIdByName(UUID organizationId, String name) {
        return departmentRepository.findAllByOrganizationIdOrderByNameAsc(organizationId).stream()
                .filter(d -> d.getName().equalsIgnoreCase(name))
                .map(d -> d.getId())
                .findFirst();
    }

    private static String buildReason(
            boolean available, boolean hasSkills, boolean enoughRest, boolean overtime, boolean overMaximum) {
        List<String> notes = new ArrayList<>();
        notes.add(available ? "available at that time" : "no matching availability declared");
        notes.add(hasSkills ? "holds the required skills" : "missing a required skill");
        if (!enoughRest) {
            notes.add("would not get the minimum rest");
        }
        if (overtime) {
            notes.add("would go into overtime");
        }
        if (overMaximum) {
            notes.add("would exceed their maximum weekly hours");
        }
        return String.join("; ", notes);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
