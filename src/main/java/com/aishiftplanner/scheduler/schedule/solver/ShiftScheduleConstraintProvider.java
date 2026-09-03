package com.aishiftplanner.scheduler.schedule.solver;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The rules that define a good schedule.
 *
 * <p>Hard constraints represent rules which must not be violated in a publishable schedule.
 * Soft constraints represent optimization goals such as fairness, wishes and staff cost.
 *
 * <p>All hour arithmetic is done in minutes using long values. This avoids floating-point
 * drift and provides enough range for larger planning problems.
 */
public class ShiftScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {

                // HARD CONSTRAINTS
                employeeMustBeAvailable(factory),
                noOverlappingShifts(factory),
                employeeMustBeClearedForLocation(factory),
                employeeMustWorkInDepartment(factory),
                requiredSkillsMustBeCovered(factory),
                minimumStaffingMustBeMet(factory),
                maximumWeeklyHoursMustNotBeExceeded(factory),
                maximumDailyHoursMustNotBeExceeded(factory),
                minimumRestBetweenShiftsMustBeRespected(factory),

                // SOFT CONSTRAINTS
                penalizeUnfilledSeats(factory),
                rewardPreferredWindows(factory),
                penalizeSchedulingOutsidePreference(factory),
                penalizeContractHoursDeviation(factory),
                penalizeOvertime(factory),
                penalizeStaffCost(factory),
                penalizeUnevenWeekendDistribution(factory),
                penalizeUnevenEveningDistribution(factory),
                penalizeUnevenClosingDistribution(factory),
                penalizeConsecutiveWorkDays(factory),
                penalizeSplitShifts(factory)
        };
    }

    // =========================================================================
    // HARD CONSTRAINTS
    // =========================================================================

    /**
     * An employee may only work inside a window they declared available or preferred.
     */
    Constraint employeeMustBeAvailable(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> !isCoveredByAvailability(slot))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Employee must be available");
    }

    /**
     * One person cannot work two overlapping shifts.
     */
    Constraint noOverlappingShifts(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                        ShiftSlot.class,
                        Joiners.equal(ShiftSlot::getEmployee))
                .filter((a, b) ->
                        a.getEmployee() != null
                                && a.overlapsInTime(b))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No overlapping shifts");
    }

    /**
     * Employees may only work at locations they are cleared for.
     */
    Constraint employeeMustBeClearedForLocation(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot ->
                        slot.isFilled()
                                && !slot.getEmployee()
                                .isClearedForLocation(
                                        slot.getShift().locationId()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Employee must be cleared for the location");
    }

    /**
     * Employees may only work in departments they are qualified for.
     */
    Constraint employeeMustWorkInDepartment(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot ->
                        slot.isFilled()
                                && !slot.getEmployee()
                                .worksInDepartment(
                                        slot.getShift().departmentId()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Employee must work in the department");
    }

    /**
     * Required skills for a shift must be covered by assigned employees.
     */
    Constraint requiredSkillsMustBeCovered(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ShiftSlot.class)
                .groupBy(
                        ShiftSlot::getShift,
                        ConstraintCollectors.toList())
                .flattenLast(
                        ShiftScheduleConstraintProvider::shortfalls)
                .penalize(
                        HardSoftScore.ONE_HARD,
                        (shift, shortfall) ->
                                shortfall.missing())
                .asConstraint("Required skills must be covered");
    }

    /**
     * Every shift must meet its configured minimum staffing requirement.
     */
    Constraint minimumStaffingMustBeMet(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ShiftSlot.class)
                .groupBy(
                        ShiftSlot::getShift,
                        ConstraintCollectors.conditionally(
                                ShiftSlot::isFilled,
                                ConstraintCollectors.count()))
                .filter((shift, filled) ->
                        filled < shift.minimumEmployees())
                .penalize(
                        HardSoftScore.ONE_HARD,
                        (shift, filled) ->
                                (long) shift.minimumEmployees() - filled)
                .asConstraint("Minimum staffing must be met");
    }

    /**
     * Weekly hours must not exceed an employee's configured maximum.
     */
    Constraint maximumWeeklyHoursMustNotBeExceeded(
            ConstraintFactory factory) {

        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .groupBy(
                        ShiftSlot::getEmployee,
                        ConstraintCollectors.sum(
                                ShiftScheduleConstraintProvider::minutesOf))
                .filter((employee, minutes) ->
                        minutes > maxWeeklyMinutes(employee))
                .penalize(
                        HardSoftScore.ONE_HARD,
                        (employee, minutes) ->
                                minutes - maxWeeklyMinutes(employee))
                .asConstraint(
                        "Maximum weekly hours must not be exceeded");
    }

    /**
     * Daily working time must stay below the configured limit.
     */
    Constraint maximumDailyHoursMustNotBeExceeded(
            ConstraintFactory factory) {

        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .groupBy(
                        ShiftSlot::getEmployee,
                        slot -> slot.window().startDate(),
                        ConstraintCollectors.sum(
                                ShiftScheduleConstraintProvider::minutesOf))
                .join(ConstraintWeights.class)
                .filter((employee, day, minutes, weights) ->
                        minutes
                                > (long)
                                (weights.maximumShiftHoursPerDay()
                                        * 60))
                .penalize(
                        HardSoftScore.ONE_HARD,
                        (employee, day, minutes, weights) ->
                                minutes
                                        - (long)
                                        (weights.maximumShiftHoursPerDay()
                                                * 60))
                .asConstraint(
                        "Maximum daily hours must not be exceeded");
    }

    /**
     * Consecutive shifts must respect the configured minimum rest period.
     */
    Constraint minimumRestBetweenShiftsMustBeRespected(
            ConstraintFactory factory) {

        return factory.forEachUniquePair(
                        ShiftSlot.class,
                        Joiners.equal(ShiftSlot::getEmployee))
                .filter((a, b) ->
                        a.getEmployee() != null
                                && !a.overlapsInTime(b))
                .join(ConstraintWeights.class)
                .filter((a, b, weights) ->
                        restMinutesBetween(a, b)
                                < (long)
                                (weights.minimumRestHours() * 60))
                .penalize(
                        HardSoftScore.ONE_HARD,
                        (a, b, weights) ->
                                (long)
                                        (weights.minimumRestHours()
                                                * 60)
                                        - restMinutesBetween(a, b))
                .asConstraint(
                        "Minimum rest between shifts");
    }

    // =========================================================================
    // SOFT CONSTRAINTS
    // =========================================================================

    /**
     * Empty seats are strongly penalized.
     *
     * <p>This is deliberately soft. An under-resourced week should still return the best
     * possible schedule and explicitly show unfilled positions.
     */
    Constraint penalizeUnfilledSeats(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ShiftSlot.class)
                .filter(slot -> !slot.isFilled())
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (slot, weights) ->
                                weights.unfilledSeatPenalty())
                .asConstraint("Unfilled seat");
    }

    /**
     * Reward an assignment that matches an employee's preferred availability window.
     */
    Constraint rewardPreferredWindows(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot ->
                        slot.isFilled()
                                && matchesType(
                                slot,
                                AvailabilityType.PREFERRED))
                .join(ConstraintWeights.class)
                .reward(
                        HardSoftScore.ONE_SOFT,
                        (slot, weights) ->
                                weights.preferredWindowReward())
                .asConstraint("Preferred window honoured");
    }

    /**
     * Penalize assigning an employee outside their preferred windows when preferences exist.
     */
    Constraint penalizeSchedulingOutsidePreference(
            ConstraintFactory factory) {

        return factory.forEach(ShiftSlot.class)
                .filter(slot ->
                        slot.isFilled()
                                && !matchesType(
                                slot,
                                AvailabilityType.PREFERRED)
                                && hasAnyPreference(slot))
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (slot, weights) ->
                                weights.nonPreferredButAvailablePenalty())
                .asConstraint(
                        "Scheduled outside stated preference");
    }

    /**
     * Penalize deviations from contract hours in either direction.
     */
    Constraint penalizeContractHoursDeviation(
            ConstraintFactory factory) {

        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .groupBy(
                        ShiftSlot::getEmployee,
                        ConstraintCollectors.sum(
                                ShiftScheduleConstraintProvider::minutesOf))
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (employee, minutes, weights) -> {

                            long target =
                                    (long)
                                            (employee.contractHoursPerWeek()
                                                    * 60);

                            if (target <= 0) {
                                return 0L;
                            }

                            long deviation =
                                    Math.abs(minutes - target);

                            return (deviation / 60)
                                    * weights
                                    .contractHoursDeviationPenalty();
                        })
                .asConstraint("Contract hours deviation");
    }

    /**
     * Penalize working hours beyond the contractual weekly hours.
     */
    Constraint penalizeOvertime(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .groupBy(
                        ShiftSlot::getEmployee,
                        ConstraintCollectors.sum(
                                ShiftScheduleConstraintProvider::minutesOf))
                .join(ConstraintWeights.class)
                .filter((employee, minutes, weights) ->
                        minutes
                                > (long)
                                (employee.contractHoursPerWeek()
                                        * 60))
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (employee, minutes, weights) -> {

                            long overtimeMinutes =
                                    minutes
                                            - (long)
                                            (employee
                                                    .contractHoursPerWeek()
                                                    * 60);

                            return (overtimeMinutes / 60)
                                    * weights.overtimePenalty();
                        })
                .asConstraint("Overtime");
    }

    /**
     * Penalize total personnel cost.
     *
     * <p>Cost is represented in cents so that score calculation stays integral.
     */
    Constraint penalizeStaffCost(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (slot, weights) -> {

                            long cents =
                                    slot.getEmployee()
                                            .costFor(
                                                    slot.durationHours())
                                            .movePointRight(2)
                                            .longValue();

                            return cents
                                    * weights.costPerCentWeight();
                        })
                .asConstraint("Staff cost");
    }

    /**
     * Distribute weekend shifts more evenly.
     */
    Constraint penalizeUnevenWeekendDistribution(
            ConstraintFactory factory) {

        return factory.forEach(ShiftSlot.class)
                .filter(slot ->
                        slot.isFilled()
                                && slot.getShift().isWeekend())
                .groupBy(
                        ShiftSlot::getEmployee,
                        ConstraintCollectors.count())
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (employee, count, weights) ->
                                squaredExcess(count)
                                        * weights
                                        .weekendFairnessPenalty())
                .asConstraint(
                        "Uneven weekend distribution");
    }

    /**
     * Distribute evening and night shifts more evenly.
     */
    Constraint penalizeUnevenEveningDistribution(
            ConstraintFactory factory) {

        return factory.forEach(ShiftSlot.class)
                .filter(slot ->
                        slot.isFilled()
                                && slot.getShift()
                                .isEveningOrNight())
                .groupBy(
                        ShiftSlot::getEmployee,
                        ConstraintCollectors.count())
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (employee, count, weights) ->
                                squaredExcess(count)
                                        * weights
                                        .eveningFairnessPenalty())
                .asConstraint(
                        "Uneven evening distribution");
    }

    /**
     * Distribute closing shifts more evenly.
     */
    Constraint penalizeUnevenClosingDistribution(
            ConstraintFactory factory) {

        return factory.forEach(ShiftSlot.class)
                .filter(slot ->
                        slot.isFilled()
                                && slot.getShift().isClosing())
                .groupBy(
                        ShiftSlot::getEmployee,
                        ConstraintCollectors.count())
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (employee, count, weights) ->
                                squaredExcess(count)
                                        * weights
                                        .closingFairnessPenalty())
                .asConstraint(
                        "Uneven closing distribution");
    }

    /**
     * Discourage assignments on consecutive working days.
     */
    Constraint penalizeConsecutiveWorkDays(
            ConstraintFactory factory) {

        return factory.forEachUniquePair(
                        ShiftSlot.class,
                        Joiners.equal(ShiftSlot::getEmployee))
                .filter((a, b) ->
                        a.getEmployee() != null
                                && areConsecutiveDays(
                                a.date(),
                                b.date()))
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (a, b, weights) ->
                                weights.consecutiveDaysPenalty())
                .asConstraint(
                        "Consecutive work days");
    }

    /**
     * Penalize separate shifts on the same day with a gap between them.
     */
    Constraint penalizeSplitShifts(
            ConstraintFactory factory) {

        return factory.forEachUniquePair(
                        ShiftSlot.class,
                        Joiners.equal(ShiftSlot::getEmployee))
                .filter((a, b) ->
                        a.getEmployee() != null
                                && a.window()
                                .startDate()
                                .equals(
                                        b.window().startDate())
                                && !a.overlapsInTime(b)
                                && restMinutesBetween(a, b) > 0)
                .join(ConstraintWeights.class)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        (a, b, weights) ->
                                weights.splitShiftPenalty())
                .asConstraint("Split shift");
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    static long minutesOf(ShiftSlot slot) {
        return slot.window()
                .duration()
                .toMinutes();
    }

    private static long maxWeeklyMinutes(
            PlanningEmployee employee) {

        return (long)
                (employee.maximumHoursPerWeek() * 60);
    }

    /**
     * Minutes of rest between two non-overlapping slots, regardless of their order.
     */
    static long restMinutesBetween(
            ShiftSlot a,
            ShiftSlot b) {

        ShiftSlot earlier =
                a.window().start().isBefore(b.window().start())
                        ? a
                        : b;

        ShiftSlot later =
                earlier == a
                        ? b
                        : a;

        Duration rest =
                earlier.window()
                        .restBefore(later.window());

        return Math.max(
                0,
                rest.toMinutes());
    }

    private static boolean areConsecutiveDays(
            LocalDate a,
            LocalDate b) {

        return Math.abs(
                java.time.temporal.ChronoUnit.DAYS.between(
                        a,
                        b))
                == 1;
    }

    /**
     * Excess beyond a single shift, squared.
     */
    private static long squaredExcess(long count) {
        long excess =
                Math.max(
                        0,
                        count - 1);

        return excess * excess;
    }

    /**
     * Checks whether the employee has declared availability covering the shift.
     */
    static boolean isCoveredByAvailability(ShiftSlot slot) {
        if (!slot.isFilled()) {
            return true;
        }

        PlanningEmployee employee =
                slot.getEmployee();

        LocalDate date =
                slot.getShift().date();

        Set<AvailabilitySlot> declared =
                employee.availabilityByDate().get(date);

        if (declared == null || declared.isEmpty()) {
            return false;
        }

        var start =
                slot.getShift().startTime();

        var end =
                slot.getShift().crossesMidnight()
                        ? com.aishiftplanner.scheduler.shared.domain.TimeWindow.END_OF_DAY
                        : slot.getShift().endTime();

        boolean blocked =
                declared.stream()
                        .anyMatch(a ->
                                a.type()
                                        == AvailabilityType.UNAVAILABLE
                                        && a.overlaps(
                                        start,
                                        end));

        if (blocked) {
            return false;
        }

        return declared.stream()
                .anyMatch(a ->
                        a.type()
                                != AvailabilityType.UNAVAILABLE
                                && a.covers(
                                start,
                                end));
    }

    /**
     * Checks if the employee's availability for this shift has the specified type.
     */
    private static boolean matchesType(
            ShiftSlot slot,
            AvailabilityType type) {

        Set<AvailabilitySlot> declared =
                slot.getEmployee()
                        .availabilityByDate()
                        .get(slot.getShift().date());

        if (declared == null) {
            return false;
        }

        var start =
                slot.getShift().startTime();

        var end =
                slot.getShift().crossesMidnight()
                        ? com.aishiftplanner.scheduler.shared.domain.TimeWindow.END_OF_DAY
                        : slot.getShift().endTime();

        return declared.stream()
                .anyMatch(a ->
                        a.type() == type
                                && a.covers(
                                start,
                                end));
    }

    /**
     * Checks whether the employee has expressed any preferred availability at all.
     */
    private static boolean hasAnyPreference(
            ShiftSlot slot) {

        return slot.getEmployee()
                .availabilityByDate()
                .values()
                .stream()
                .flatMap(Set::stream)
                .anyMatch(a ->
                        a.type()
                                == AvailabilityType.PREFERRED);
    }

    /**
     * Per-skill shortfall on one shift.
     */
    record SkillShortfall(
            UUID shiftId,
            UUID skillId,
            long missing) {
    }

    /**
     * Determines how many holders of each required skill are missing.
     */
    static java.util.List<SkillShortfall> shortfalls(
            java.util.List<ShiftSlot> slotsOfShift) {

        if (slotsOfShift.isEmpty()) {
            return java.util.List.of();
        }

        PlanningShift shift =
                slotsOfShift.get(0).getShift();

        Map<UUID, Integer> required =
                shift.requiredSkills();

        if (required.isEmpty()) {
            return java.util.List.of();
        }

        java.util.List<SkillShortfall> result =
                new java.util.ArrayList<>();

        for (Map.Entry<UUID, Integer> entry
                : required.entrySet()) {

            long holders =
                    slotsOfShift.stream()
                            .filter(s ->
                                    s.isFilled()
                                            && s.getEmployee()
                                            .hasSkill(
                                                    entry.getKey()))
                            .count();

            long missing =
                    entry.getValue() - holders;

            if (missing > 0) {
                result.add(
                        new SkillShortfall(
                                shift.id(),
                                entry.getKey(),
                                missing));
            }
        }

        return result;
    }
}