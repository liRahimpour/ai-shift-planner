package com.aishiftplanner.scheduler.schedule.solver;

import com.aishiftplanner.scheduler.schedule.domain.PlanningStrategy;
import com.aishiftplanner.scheduler.shared.config.SchedulingProperties;

/**
 * Soft-constraint weights, plus the configurable labour limits the hard constraints read.
 *
 * <p>This record is what makes one constraint provider serve all three strategies. The
 * alternative — a constraint class per strategy — would give the hard constraints three
 * places to drift apart, and a bug in "COST_OPTIMIZED's rest-time rule" is precisely the bug
 * nobody would notice until someone worked nine hours after closing.
 *
 * <p>Hard constraints are <em>not</em> weighted here. They are absolute: no combination of
 * weights can buy a violation.
 *
 * @param minimumRestHours configurable, jurisdiction-dependent (see SchedulingProperties)
 * @param maximumShiftHoursPerDay configurable daily working-time ceiling
 * @param maximumConsecutiveWorkDays configurable ceiling on consecutive days worked
 */
public record ConstraintWeights(
        PlanningStrategy strategy,
        long unfilledSeatPenalty,
        long belowPreferredStaffingPenalty,
        long preferredWindowReward,
        long nonPreferredButAvailablePenalty,
        long contractHoursDeviationPenalty,
        long overtimePenalty,
        long underUtilisationPenalty,
        long costPerCentWeight,
        long weekendFairnessPenalty,
        long eveningFairnessPenalty,
        long closingFairnessPenalty,
        long consecutiveDaysPenalty,
        long splitShiftPenalty,
        long shiftContinuityReward,
        double minimumRestHours,
        double maximumShiftHoursPerDay,
        int maximumConsecutiveWorkDays) {

    /**
     * Prioritizes even distribution of unpopular work and honouring wishes; cost is barely
     * considered. For operators who care most about staff retention.
     */
    public static ConstraintWeights fair(SchedulingProperties properties) {
        return new ConstraintWeights(
                PlanningStrategy.FAIR,
                /* unfilledSeatPenalty */ 1_000_000,
                /* belowPreferredStaffingPenalty */ 5_000,
                /* preferredWindowReward */ 800,
                /* nonPreferredButAvailablePenalty */ 60,
                /* contractHoursDeviationPenalty */ 500,
                /* overtimePenalty */ 900,
                /* underUtilisationPenalty */ 500,
                /* costPerCentWeight */ 1,
                /* weekendFairnessPenalty */ 1_200,
                /* eveningFairnessPenalty */ 900,
                /* closingFairnessPenalty */ 1_100,
                /* consecutiveDaysPenalty */ 400,
                /* splitShiftPenalty */ 350,
                /* shiftContinuityReward */ 120,
                properties.minimumRestHoursBetweenShifts(),
                properties.maximumShiftHoursPerDay(),
                properties.maximumConsecutiveWorkDays());
    }

    /**
     * Minimizes staff cost and overtime and avoids staffing above the minimum.
     *
     * <p>Note that fairness and preference weights are reduced, not zeroed. A schedule that
     * is purely cost-driven is cheap for exactly one rota cycle, after which the people who
     * always got the closing shifts start leaving — and no solver models the cost of that.
     */
    public static ConstraintWeights costOptimized(SchedulingProperties properties) {
        return new ConstraintWeights(
                PlanningStrategy.COST_OPTIMIZED,
                /* unfilledSeatPenalty */ 1_000_000,
                /* belowPreferredStaffingPenalty */ 300,
                /* preferredWindowReward */ 150,
                /* nonPreferredButAvailablePenalty */ 10,
                /* contractHoursDeviationPenalty */ 200,
                /* overtimePenalty */ 4_000,
                /* underUtilisationPenalty */ 1_200,
                /* costPerCentWeight */ 12,
                /* weekendFairnessPenalty */ 200,
                /* eveningFairnessPenalty */ 150,
                /* closingFairnessPenalty */ 200,
                /* consecutiveDaysPenalty */ 150,
                /* splitShiftPenalty */ 100,
                /* shiftContinuityReward */ 200,
                properties.minimumRestHoursBetweenShifts(),
                properties.maximumShiftHoursPerDay(),
                properties.maximumConsecutiveWorkDays());
    }

    /** The default recommendation: cost, fairness, wishes, coverage and contract hours together. */
    public static ConstraintWeights balanced(SchedulingProperties properties) {
        return new ConstraintWeights(
                PlanningStrategy.BALANCED,
                /* unfilledSeatPenalty */ 1_000_000,
                /* belowPreferredStaffingPenalty */ 2_000,
                /* preferredWindowReward */ 450,
                /* nonPreferredButAvailablePenalty */ 30,
                /* contractHoursDeviationPenalty */ 400,
                /* overtimePenalty */ 2_000,
                /* underUtilisationPenalty */ 800,
                /* costPerCentWeight */ 5,
                /* weekendFairnessPenalty */ 700,
                /* eveningFairnessPenalty */ 500,
                /* closingFairnessPenalty */ 650,
                /* consecutiveDaysPenalty */ 300,
                /* splitShiftPenalty */ 220,
                /* shiftContinuityReward */ 160,
                properties.minimumRestHoursBetweenShifts(),
                properties.maximumShiftHoursPerDay(),
                properties.maximumConsecutiveWorkDays());
    }

    /** Defaults for tests and for a solution constructed without explicit weights. */
    public static ConstraintWeights balanced() {
        return balanced(new SchedulingProperties(11, 10, 6, 30));
    }

    public static ConstraintWeights forStrategy(PlanningStrategy strategy, SchedulingProperties properties) {
        return switch (strategy) {
            case FAIR -> fair(properties);
            case COST_OPTIMIZED -> costOptimized(properties);
            case BALANCED, MANUAL -> balanced(properties);
        };
    }
}
