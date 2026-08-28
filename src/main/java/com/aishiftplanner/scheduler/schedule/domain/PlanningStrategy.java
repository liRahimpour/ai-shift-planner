package com.aishiftplanner.scheduler.schedule.domain;

/**
 * The three optimization profiles a planning run produces, plus a marker for hand-built
 * schedules.
 *
 * <p>All three solve the <em>same</em> hard constraints — they differ only in how the soft
 * constraints are weighted. That is the whole point: a manager choosing "cheapest" must never
 * be choosing "cheapest, and also someone works without their legal rest".
 */
public enum PlanningStrategy {

    /** Even distribution of weekends, evenings and closing shifts; honours wishes and contract hours. */
    FAIR,

    /** Minimizes staff cost and overtime, avoids overstaffing beyond the minimum. */
    COST_OPTIMIZED,

    /** Balances cost, fairness, wishes, coverage and contract hours. The default recommendation. */
    BALANCED,

    /** Built or heavily edited by hand rather than produced by a solver run. */
    MANUAL
}
