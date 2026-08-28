package com.aishiftplanner.scheduler.schedule.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
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
 * <p>Structure: hard constraints come first and are absolute — a violated hard constraint
 * makes the schedule infeasible and unpublishable, and no amount of soft score can buy one.
 * Soft constraints follow and are weighted by {@link ConstraintWeights}, which is what makes
 * FAIR, COST_OPTIMIZED and BALANCED three different answers to the same problem rather than
 * three different rule sets.
 *
 * <p>All hour arithmetic is done in <b>minutes as longs</b>, never as doubles. Floating-point
 * accumulation over hundreds of shifts drifts, and a fairness comparison that flips because
 * of a rounding error is impossible to explain to the person who got the extra Saturday.
 */
public class ShiftScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            // --- Hard: never violated in a publishable schedule ---------------
            employeeMustBeAvailable(factory),
            noOverlappingShifts(factory),
            employeeMustBeClearedForLocation(factory),
            employeeMustWorkInDepartment(factory),
            requiredSkillsMustBeCovered(factory),
            minimumStaffingMustBeMet(factory),
            maximumWeeklyHoursMustNotBeExceeded(factory),
            maximumDailyHoursMustNotBeExceeded(factory),
            minimumRestBetweenShiftsMustBeRespected(factory),

            // --- Soft: trade-offs, weighted per strategy -----------------------
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
            penalizeSplitShifts(factory),
        };
    }

    // =========================================================================
    // HARD CONSTRAINTS
    // =========================================================================

    /**
     * An employee may only work inside a window they declared available (or preferred), and
     * never inside one they marked unavailable.
     *
     * <p>Note the default for a day with <em>no</em> declaration: not available. Treating
     * silence as consent is how people end up rostered on the day they were at a funeral.
     * The manager dashboard exists precisely so that missing submissions are visible before
     * planning starts, rather than being papered over here.
     */
    Constraint employeeMustBeAvailable(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> !isCoveredByAvailability(slot))
                .penalize(HardSoftLongScore.ONE_HARD)
                .asConstraint("Employee must be available");
    }

    /** One person cannot be in two places at once. */
    Constraint noOverlappingShifts(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                        ShiftSlot.class, Joiners.equal(ShiftSlot::getEmployee))
                .filter((a, b) -> a.getEmployee() != null && a.overlapsInTime(b))
                .penalize(HardSoftLongScore.ONE_HARD)
                .asConstraint("No overlapping shifts");
    }

    /** Staff may only be scheduled at locations they are cleared for. */
    Constraint employeeMustBeClearedForLocation(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> slot.isFilled()
                        && !slot.getEmployee().isClearedForLocation(slot.getShift().locationId()))
                .penalize(HardSoftLongScore.ONE_HARD)
                .asConstraint("Employee must be cleared for the location");
    }

    /**
     * Staff may only be scheduled in departments they belong to.
     *
     * <p>An employee with no departments recorded is treated as universally deployable rather
     * than as unschedulable — otherwise incomplete master data would silently make the whole
     * period unsolvable, which is a far worse failure than a slightly loose assignment a
     * manager can correct.
     */
    Constraint employeeMustWorkInDepartment(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> slot.isFilled()
                        && !slot.getEmployee().worksInDepartment(slot.getShift().departmentId()))
                .penalize(HardSoftLongScore.ONE_HARD)
                .asConstraint("Employee must work in the department");
    }

    /**
     * Every skill a shift demands must be held by at least as many assigned people as
     * demanded ("3 people, of whom 1× BAR and 1× CLOSING").
     *
     * <p>Grouped per shift and per skill: the rule is about the composition of the team on a
     * shift, not about any individual assignment, so it cannot be expressed one seat at a
     * time.
     */
    Constraint requiredSkillsMustBeCovered(ConstraintFactory factory) {
        // Includes unassigned seats so that a shift nobody was assigned to still reports its
        // full skill shortfall rather than vanishing from the stream.
        return factory.forEachIncludingUnassigned(ShiftSlot.class)
                .groupBy(ShiftSlot::getShift, ConstraintCollectors.toList())
                .flattenLast(ShiftScheduleConstraintProvider::shortfalls)
                .penalizeLong(HardSoftLongScore.ONE_HARD, (shift, shortfall) -> shortfall.missing())
                .asConstraint("Required skills must be covered");
    }

    /**
     * A shift may not fall below its minimum staffing.
     *
     * <p>Uses {@code forEachIncludingUnassigned} so that a shift whose seats are all empty is
     * still evaluated. With a plain {@code forEach}, a completely unstaffed shift would
     * disappear from this stream and score as if it were fine — the worst possible silent
     * failure for a coverage rule.
     */
    Constraint minimumStaffingMustBeMet(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ShiftSlot.class)
                .groupBy(
                        slot -> slot.getShift(),
                        ConstraintCollectors.conditionally(
                                ShiftSlot::isFilled, ConstraintCollectors.count()))
                .filter((shift, filled) -> filled < shift.minimumEmployees())
                .penalizeLong(
                        HardSoftLongScore.ONE_HARD,
                        (shift, filled) -> (long) shift.minimumEmployees() - filled)
                .asConstraint("Minimum staffing must be met");
    }

    /** Weekly hours must stay within the employee's configured maximum. */
    Constraint maximumWeeklyHoursMustNotBeExceeded(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.sumLong(ShiftScheduleConstraintProvider::minutesOf))
                .filter((employee, minutes) -> minutes > maxWeeklyMinutes(employee))
                .penalizeLong(
                        HardSoftLongScore.ONE_HARD,
                        (employee, minutes) -> minutes - maxWeeklyMinutes(employee))
                .asConstraint("Maximum weekly hours must not be exceeded");
    }

    /**
     * Daily working time must stay within the configured ceiling.
     *
     * <p>Grouped by (employee, calendar day of the shift's start). A shift that runs past
     * midnight counts against the day it started, which is how working-time rules and payroll
     * both treat it — and, more practically, is the only grouping under which "closing then
     * opening" shows up as the problem it is.
     */
    Constraint maximumDailyHoursMustNotBeExceeded(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .groupBy(
                        ShiftSlot::getEmployee,
                        slot -> slot.window().startDate(),
                        ConstraintCollectors.sumLong(ShiftScheduleConstraintProvider::minutesOf))
                .join(ConstraintWeights.class)
                .filter((employee, day, minutes, weights) ->
                        minutes > (long) (weights.maximumShiftHoursPerDay() * 60))
                .penalizeLong(
                        HardSoftLongScore.ONE_HARD,
                        (employee, day, minutes, weights) ->
                                minutes - (long) (weights.maximumShiftHoursPerDay() * 60))
                .asConstraint("Maximum daily hours must not be exceeded");
    }

    /**
     * Consecutive shifts must be separated by at least the configured rest period.
     *
     * <p>The value comes from configuration, not from a constant in this file: 11 hours is
     * the German figure, and this product is meant to run under other rules too. Hardcoding
     * it would make the solver quietly wrong the first time it is deployed elsewhere.
     */
    Constraint minimumRestBetweenShiftsMustBeRespected(ConstraintFactory factory) {
        return factory.forEachUniquePair(ShiftSlot.class, Joiners.equal(ShiftSlot::getEmployee))
                .filter((a, b) -> a.getEmployee() != null && !a.overlapsInTime(b))
                .join(ConstraintWeights.class)
                .filter((a, b, weights) -> restMinutesBetween(a, b) < (long) (weights.minimumRestHours() * 60))
                .penalizeLong(
                        HardSoftLongScore.ONE_HARD,
                        (a, b, weights) ->
                                (long) (weights.minimumRestHours() * 60) - restMinutesBetween(a, b))
                .asConstraint("Minimum rest between shifts");
    }

    // =========================================================================
    // SOFT CONSTRAINTS
    // =========================================================================

    /**
     * Every seat left empty is penalized heavily.
     *
     * <p>Soft rather than hard, and that distinction is the point: an under-resourced week
     * must still produce the best possible plan plus an explicit list of what could not be
     * filled. Making it hard would make the solver's only answer "infeasible", which tells a
     * manager nothing they can act on.
     */
    Constraint penalizeUnfilledSeats(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ShiftSlot.class)
                .filter(slot -> !slot.isFilled())
                .join(ConstraintWeights.class)
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (slot, weights) -> weights.unfilledSeatPenalty())
                .asConstraint("Unfilled seat");
    }

    /** Reward assignments that fall inside a window the employee actively asked for. */
    Constraint rewardPreferredWindows(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> slot.isFilled() && matchesType(slot, AvailabilityType.PREFERRED))
                .join(ConstraintWeights.class)
                .rewardLong(HardSoftLongScore.ONE_SOFT, (slot, weights) -> weights.preferredWindowReward())
                .asConstraint("Preferred window honoured");
    }

    /**
     * Mildly penalize using someone's merely-available time when they expressed preferences
     * elsewhere. Without this, "AVAILABLE" and "PREFERRED" would be interchangeable and
     * declaring a preference would be pointless.
     */
    Constraint penalizeSchedulingOutsidePreference(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> slot.isFilled()
                        && !matchesType(slot, AvailabilityType.PREFERRED)
                        && hasAnyPreference(slot))
                .join(ConstraintWeights.class)
                .penalizeLong(
                        HardSoftLongScore.ONE_SOFT,
                        (slot, weights) -> weights.nonPreferredButAvailablePenalty())
                .asConstraint("Scheduled outside stated preference");
    }

    /** Penalize deviation from contract hours in both directions. */
    Constraint penalizeContractHoursDeviation(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.sumLong(ShiftScheduleConstraintProvider::minutesOf))
                .join(ConstraintWeights.class)
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (employee, minutes, weights) -> {
                    long target = (long) (employee.contractHoursPerWeek() * 60);
                    if (target <= 0) {
                        return 0L;
                    }
                    long deviation = Math.abs(minutes - target);
                    // Per hour of deviation, so a 30-minute mismatch is not treated the same
                    // as a five-hour one.
                    return (deviation / 60) * weights.contractHoursDeviationPenalty();
                })
                .asConstraint("Contract hours deviation");
    }

    /** Penalize hours beyond contract hours specifically (overtime costs more than it looks). */
    Constraint penalizeOvertime(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.sumLong(ShiftScheduleConstraintProvider::minutesOf))
                .join(ConstraintWeights.class)
                .filter((employee, minutes, weights) -> minutes > (long) (employee.contractHoursPerWeek() * 60))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (employee, minutes, weights) -> {
                    long overtimeMinutes = minutes - (long) (employee.contractHoursPerWeek() * 60);
                    return (overtimeMinutes / 60) * weights.overtimePenalty();
                })
                .asConstraint("Overtime");
    }

    /**
     * Total staff cost, in cents, weighted by strategy.
     *
     * <p>Cents rather than euros so the whole score stays integral — mixing a floating-point
     * cost term into an integer score is how two runs of the same problem end up with
     * different "best" answers.
     */
    Constraint penalizeStaffCost(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(ShiftSlot::isFilled)
                .join(ConstraintWeights.class)
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (slot, weights) -> {
                    long cents = slot.getEmployee()
                            .costFor(slot.durationHours())
                            .movePointRight(2)
                            .longValue();
                    return cents * weights.costPerCentWeight();
                })
                .asConstraint("Staff cost");
    }

    /**
     * Fairness constraints penalize <em>each</em> weekend/evening/closing shift a person
     * works, quadratically in effect: because the penalty applies per shift, a solver
     * minimizing the total naturally spreads them out rather than piling them on whoever
     * happens to be most available. Explicit variance would be more precise but would require
     * a full pass over the solution on every move, which is far too slow for the inner loop.
     */
    Constraint penalizeUnevenWeekendDistribution(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> slot.isFilled() && slot.getShift().isWeekend())
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.count())
                .join(ConstraintWeights.class)
                .penalizeLong(
                        HardSoftLongScore.ONE_SOFT,
                        (employee, count, weights) -> squaredExcess(count) * weights.weekendFairnessPenalty())
                .asConstraint("Uneven weekend distribution");
    }

    Constraint penalizeUnevenEveningDistribution(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> slot.isFilled() && slot.getShift().isEveningOrNight())
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.count())
                .join(ConstraintWeights.class)
                .penalizeLong(
                        HardSoftLongScore.ONE_SOFT,
                        (employee, count, weights) -> squaredExcess(count) * weights.eveningFairnessPenalty())
                .asConstraint("Uneven evening distribution");
    }

    Constraint penalizeUnevenClosingDistribution(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> slot.isFilled() && slot.getShift().isClosing())
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.count())
                .join(ConstraintWeights.class)
                .penalizeLong(
                        HardSoftLongScore.ONE_SOFT,
                        (employee, count, weights) -> squaredExcess(count) * weights.closingFairnessPenalty())
                .asConstraint("Uneven closing distribution");
    }

    /** Discourage long runs of consecutive working days. */
    Constraint penalizeConsecutiveWorkDays(ConstraintFactory factory) {
        return factory.forEachUniquePair(ShiftSlot.class, Joiners.equal(ShiftSlot::getEmployee))
                .filter((a, b) -> a.getEmployee() != null && areConsecutiveDays(a.date(), b.date()))
                .join(ConstraintWeights.class)
                .penalizeLong(
                        HardSoftLongScore.ONE_SOFT,
                        (a, b, weights) -> weights.consecutiveDaysPenalty())
                .asConstraint("Consecutive work days");
    }

    /**
     * Penalize two separate shifts on the same day with a gap between them — the split shift
     * that turns a six-hour day into a twelve-hour one for the person living it.
     */
    Constraint penalizeSplitShifts(ConstraintFactory factory) {
        return factory.forEachUniquePair(ShiftSlot.class, Joiners.equal(ShiftSlot::getEmployee))
                .filter((a, b) -> a.getEmployee() != null
                        && a.window().startDate().equals(b.window().startDate())
                        && !a.overlapsInTime(b)
                        && restMinutesBetween(a, b) > 0)
                .join(ConstraintWeights.class)
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (a, b, weights) -> weights.splitShiftPenalty())
                .asConstraint("Split shift");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    static long minutesOf(ShiftSlot slot) {
        return slot.window().duration().toMinutes();
    }

    private static long maxWeeklyMinutes(PlanningEmployee employee) {
        return (long) (employee.maximumHoursPerWeek() * 60);
    }

    /**
     * Minutes of rest between two non-overlapping slots, regardless of their order.
     * Returns 0 for slots that touch exactly.
     */
    static long restMinutesBetween(ShiftSlot a, ShiftSlot b) {
        ShiftSlot earlier = a.window().start().isBefore(b.window().start()) ? a : b;
        ShiftSlot later = earlier == a ? b : a;
        Duration rest = earlier.window().restBefore(later.window());
        return Math.max(0, rest.toMinutes());
    }

    private static boolean areConsecutiveDays(LocalDate a, LocalDate b) {
        return Math.abs(java.time.temporal.ChronoUnit.DAYS.between(a, b)) == 1;
    }

    /**
     * Excess beyond a single shift, squared.
     *
     * <p>Squaring is what makes the constraint about <em>distribution</em> rather than
     * <em>volume</em>: four evening shifts for one person costs 9 while one each for four
     * people costs 0, so the solver actively prefers spreading them.
     */
    private static long squaredExcess(long count) {
        long excess = Math.max(0, count - 1);
        return excess * excess;
    }

    /** True if the employee declared a window that fully covers this slot and is not a block. */
    static boolean isCoveredByAvailability(ShiftSlot slot) {
        if (!slot.isFilled()) {
            // An empty seat breaks no availability rule; it is handled by the unfilled-seat
            // and minimum-staffing constraints instead.
            return true;
        }
        PlanningEmployee employee = slot.getEmployee();
        LocalDate date = slot.getShift().date();
        Set<AvailabilitySlot> declared = employee.availabilityByDate().get(date);
        if (declared == null || declared.isEmpty()) {
            return false; // silence is not consent
        }

        var start = slot.getShift().startTime();
        // For a midnight-crossing shift, the availability check uses the portion that falls
        // on the shift's start date; the remainder is covered by the next day's declaration
        // if the employee made one, and by the rest-time constraint either way.
        var end = slot.getShift().crossesMidnight() ? java.time.LocalTime.MAX : slot.getShift().endTime();

        boolean blocked = declared.stream()
                .anyMatch(a -> a.type() == AvailabilityType.UNAVAILABLE && a.overlaps(start, end));
        if (blocked) {
            return false;
        }
        return declared.stream()
                .anyMatch(a -> a.type() != AvailabilityType.UNAVAILABLE && a.covers(start, end));
    }

    private static boolean matchesType(ShiftSlot slot, AvailabilityType type) {
        Set<AvailabilitySlot> declared =
                slot.getEmployee().availabilityByDate().get(slot.getShift().date());
        if (declared == null) {
            return false;
        }
        var start = slot.getShift().startTime();
        var end = slot.getShift().crossesMidnight() ? java.time.LocalTime.MAX : slot.getShift().endTime();
        return declared.stream().anyMatch(a -> a.type() == type && a.covers(start, end));
    }

    private static boolean hasAnyPreference(ShiftSlot slot) {
        return slot.getEmployee().availabilityByDate().values().stream()
                .flatMap(Set::stream)
                .anyMatch(a -> a.type() == AvailabilityType.PREFERRED);
    }

    /** Per-skill shortfall on one shift. */
    record SkillShortfall(UUID shiftId, UUID skillId, long missing) {
    }

    /**
     * How many holders of each demanded skill are still missing from a shift's assigned team.
     *
     * <p>One employee holding both BAR and CLOSING satisfies a "1× BAR, 1× CLOSING" demand,
     * which is correct: the requirement is that the skills are present on the floor, not that
     * they are held by different people. A manager who needs two distinct people asks for
     * two of each.
     */
    static java.util.List<SkillShortfall> shortfalls(java.util.List<ShiftSlot> slotsOfShift) {
        if (slotsOfShift.isEmpty()) {
            return java.util.List.of();
        }
        PlanningShift shift = slotsOfShift.get(0).getShift();
        Map<UUID, Integer> required = shift.requiredSkills();
        if (required.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<SkillShortfall> result = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : required.entrySet()) {
            long holders = slotsOfShift.stream()
                    .filter(s -> s.isFilled() && s.getEmployee().hasSkill(entry.getKey()))
                    .count();
            long missing = entry.getValue() - holders;
            if (missing > 0) {
                result.add(new SkillShortfall(shift.id(), entry.getKey(), missing));
            }
        }
        return result;
    }
}
