package com.aishiftplanner.scheduler.schedule.domain;

public enum ShiftStatus {
    /** Generated or hand-created, not yet part of a solved schedule. */
    DRAFT,
    /** Included in a solver result that a manager is reviewing. */
    PLANNED,
    /** Visible to employees. */
    PUBLISHED,
    /** Published and frozen — no further edits without an explicit reopen. */
    LOCKED
}
