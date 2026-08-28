package com.aishiftplanner.scheduler.schedule.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import java.time.LocalDate;
import java.util.List;

/**
 * The planning solution: all seats to fill, all people who could fill them, and the weights
 * that decide what "better" means.
 *
 * <p>Score type is {@link HardSoftLongScore}: hard for rules that must never be broken
 * (availability, double-booking, skills, rest time, location clearance, minimum staffing),
 * soft for everything that is a trade-off (cost, fairness, wishes, contract hours). A
 * negative hard score means the schedule is not publishable, full stop — no amount of soft
 * score can compensate, which is exactly the property that keeps "cheapest" from quietly
 * meaning "illegal". {@code long} rather than {@code int} because staff cost in cents across
 * a large multi-location week overflows an int surprisingly easily.
 */
@PlanningSolution
public class ShiftSchedule {

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "employeeRange")
    private List<PlanningEmployee> employees;

    @PlanningEntityCollectionProperty
    private List<ShiftSlot> slots;

    /**
     * The constraint weights that distinguish FAIR from COST_OPTIMIZED from BALANCED.
     *
     * <p>A problem fact rather than a hardcoded constant, so the same constraint code serves
     * all three strategies. Three parallel copies of the constraint provider — one per
     * strategy — would be three places for the hard constraints to drift apart, which is the
     * one thing that must never happen.
     */
    @ProblemFactCollectionProperty
    private List<ConstraintWeights> weightsHolder;

    /** Boundaries of the planning period, needed for per-week hour accounting. */
    private LocalDate periodStart;

    private LocalDate periodEnd;

    @PlanningScore
    private HardSoftLongScore score;

    /** Required by Timefold. */
    public ShiftSchedule() {
    }

    public ShiftSchedule(
            List<PlanningEmployee> employees,
            List<ShiftSlot> slots,
            ConstraintWeights weights,
            LocalDate periodStart,
            LocalDate periodEnd) {
        this.employees = employees;
        this.slots = slots;
        this.weightsHolder = List.of(weights);
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public ConstraintWeights weights() {
        return weightsHolder == null || weightsHolder.isEmpty()
                ? ConstraintWeights.balanced()
                : weightsHolder.get(0);
    }

    /** Number of weeks the period spans, at least 1 — used to scale weekly hour targets. */
    public double weeksInPeriod() {
        if (periodStart == null || periodEnd == null) {
            return 1.0;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        return Math.max(1.0, days / 7.0);
    }

    public List<PlanningEmployee> getEmployees() {
        return employees;
    }

    public void setEmployees(List<PlanningEmployee> employees) {
        this.employees = employees;
    }

    public List<ShiftSlot> getSlots() {
        return slots;
    }

    public void setSlots(List<ShiftSlot> slots) {
        this.slots = slots;
    }

    public List<ConstraintWeights> getWeightsHolder() {
        return weightsHolder;
    }

    public void setWeightsHolder(List<ConstraintWeights> weightsHolder) {
        this.weightsHolder = weightsHolder;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public HardSoftLongScore getScore() {
        return score;
    }

    public void setScore(HardSoftLongScore score) {
        this.score = score;
    }
}
