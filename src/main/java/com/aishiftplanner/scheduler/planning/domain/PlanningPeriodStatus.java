package com.aishiftplanner.scheduler.planning.domain;

/**
 * Lifecycle of a planning period.
 *
 * <pre>
 * OPEN_FOR_AVAILABILITY → READY_FOR_PLANNING → PLANNING → DRAFT → PUBLISHED → ARCHIVED
 *            ↑                    │
 *            └──── reopened ──────┘   (manager may reopen the availability window)
 * </pre>
 */
public enum PlanningPeriodStatus {

    /** Employees may add and edit availability and comments until the deadline passes. */
    OPEN_FOR_AVAILABILITY,

    /** Deadline has passed (or was closed early); availability is frozen, planning may start. */
    READY_FOR_PLANNING,

    /** A solver run is in flight. */
    PLANNING,

    /** Proposals exist and a manager is reviewing/editing them; not yet visible to employees. */
    DRAFT,

    /** The selected schedule is live and visible to employees. */
    PUBLISHED,

    /** Historical; read-only. */
    ARCHIVED;

    /** Availability may only be edited by employees while the period is open. */
    public boolean allowsAvailabilityEditing() {
        return this == OPEN_FOR_AVAILABILITY;
    }

    public boolean allowsPlanning() {
        return this == READY_FOR_PLANNING || this == DRAFT;
    }

    public boolean isFinal() {
        return this == ARCHIVED;
    }
}
