package com.aishiftplanner.scheduler.schedule.solver;

import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builders for constraint tests.
 *
 * <p>Constraint tests are only useful if writing one is nearly free — otherwise the awkward
 * edge cases (the midnight-crossing bar shift, the person with no declared availability) never
 * get written, and those are exactly the cases that break in production.
 */
final class SolverTestData {

    static final LocalDate SATURDAY = LocalDate.of(2026, 9, 12);
    static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);
    static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    static final UUID LOCATION = UUID.randomUUID();
    static final UUID OTHER_LOCATION = UUID.randomUUID();
    static final UUID BAR_DEPARTMENT = UUID.randomUUID();
    static final UUID KITCHEN_DEPARTMENT = UUID.randomUUID();
    static final UUID BAR_SKILL = UUID.randomUUID();
    static final UUID CLOSING_SKILL = UUID.randomUUID();

    private SolverTestData() {
    }

    static EmployeeBuilder employee(String name) {
        return new EmployeeBuilder(name);
    }

    static final class EmployeeBuilder {
        private final String name;
        private UUID homeLocation = LOCATION;
        private Set<UUID> clearedLocations = Set.of();
        private Set<UUID> skills = Set.of();
        private Set<UUID> departments = Set.of();
        private BigDecimal wage = new BigDecimal("15.00");
        private double contractHours = 30;
        private double minimumHours = 0;
        private double maximumHours = 48;
        private final Map<LocalDate, Set<AvailabilitySlot>> availability = new LinkedHashMap<>();

        private EmployeeBuilder(String name) {
            this.name = name;
        }

        EmployeeBuilder at(UUID locationId) {
            this.homeLocation = locationId;
            return this;
        }

        EmployeeBuilder alsoClearedFor(UUID locationId) {
            this.clearedLocations = Set.of(locationId);
            return this;
        }

        EmployeeBuilder withSkills(UUID... skillIds) {
            this.skills = Set.of(skillIds);
            return this;
        }

        EmployeeBuilder inDepartments(UUID... departmentIds) {
            this.departments = Set.of(departmentIds);
            return this;
        }

        EmployeeBuilder earning(String hourlyWage) {
            this.wage = new BigDecimal(hourlyWage);
            return this;
        }

        EmployeeBuilder withContractHours(double hours) {
            this.contractHours = hours;
            return this;
        }

        EmployeeBuilder withMaximumHours(double hours) {
            this.maximumHours = hours;
            return this;
        }

        /** Declares whole-day availability of the given type. */
        EmployeeBuilder availableAllDay(LocalDate date, AvailabilityType type) {
            availability.computeIfAbsent(date, d -> new java.util.LinkedHashSet<>())
                    .add(new AvailabilitySlot(type, null, null));
            return this;
        }

        EmployeeBuilder available(LocalDate date, AvailabilityType type, String from, String to) {
            availability.computeIfAbsent(date, d -> new java.util.LinkedHashSet<>())
                    .add(new AvailabilitySlot(type, LocalTime.parse(from), LocalTime.parse(to)));
            return this;
        }

        PlanningEmployee build() {
            return new PlanningEmployee(
                    UUID.randomUUID(),
                    name,
                    homeLocation,
                    clearedLocations,
                    skills,
                    departments,
                    wage,
                    contractHours,
                    minimumHours,
                    maximumHours,
                    Map.copyOf(availability));
        }
    }

    static PlanningShift shift(
            LocalDate date, String from, String to, boolean crossesMidnight, int required, int minimum) {
        return new PlanningShift(
                UUID.randomUUID(),
                LOCATION,
                BAR_DEPARTMENT,
                date,
                LocalTime.parse(from),
                LocalTime.parse(to),
                crossesMidnight,
                required,
                minimum,
                Map.of(),
                com.aishiftplanner.scheduler.shared.domain.TimeWindow.of(
                        date, LocalTime.parse(from), LocalTime.parse(to), crossesMidnight));
    }

    static PlanningShift shiftRequiringSkills(
            LocalDate date, String from, String to, int required, Map<UUID, Integer> skills) {
        return new PlanningShift(
                UUID.randomUUID(),
                LOCATION,
                BAR_DEPARTMENT,
                date,
                LocalTime.parse(from),
                LocalTime.parse(to),
                false,
                required,
                required,
                skills,
                com.aishiftplanner.scheduler.shared.domain.TimeWindow.of(
                        date, LocalTime.parse(from), LocalTime.parse(to), false));
    }

    static PlanningShift shiftInDepartment(UUID departmentId, LocalDate date, String from, String to) {
        return new PlanningShift(
                UUID.randomUUID(),
                LOCATION,
                departmentId,
                date,
                LocalTime.parse(from),
                LocalTime.parse(to),
                false,
                1,
                1,
                Map.of(),
                com.aishiftplanner.scheduler.shared.domain.TimeWindow.of(
                        date, LocalTime.parse(from), LocalTime.parse(to), false));
    }

    static PlanningShift shiftAtLocation(UUID locationId, LocalDate date, String from, String to) {
        return new PlanningShift(
                UUID.randomUUID(),
                locationId,
                BAR_DEPARTMENT,
                date,
                LocalTime.parse(from),
                LocalTime.parse(to),
                false,
                1,
                1,
                Map.of(),
                com.aishiftplanner.scheduler.shared.domain.TimeWindow.of(
                        date, LocalTime.parse(from), LocalTime.parse(to), false));
    }

    static ShiftSlot slot(PlanningShift shift, int index, PlanningEmployee employee) {
        ShiftSlot slot = new ShiftSlot(UUID.randomUUID(), shift, index);
        slot.setEmployee(employee);
        return slot;
    }

    static ShiftSlot emptySlot(PlanningShift shift, int index) {
        return new ShiftSlot(UUID.randomUUID(), shift, index);
    }
}
