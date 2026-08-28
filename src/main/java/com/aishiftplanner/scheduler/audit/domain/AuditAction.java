package com.aishiftplanner.scheduler.audit.domain;

/**
 * The consequential actions this system records.
 *
 * <p>The list is deliberately short and business-meaningful rather than a generic
 * CREATE/UPDATE/DELETE log of every table. An audit trail is only useful if someone can read
 * it later and reconstruct what a human decided — "deadline reopened by X on Y" answers a
 * real question; "row updated" does not.
 */
public enum AuditAction {

    AVAILABILITY_CHANGED,
    AVAILABILITY_DEADLINE_CHANGED,
    AVAILABILITY_REOPENED,
    COMMENT_SUBMITTED,
    COMMENT_INTERPRETATION_REVIEWED,
    PLANNING_PERIOD_CREATED,
    PLANNING_PERIOD_STATUS_CHANGED,
    STAFFING_REQUIREMENT_CHANGED,
    SCHEDULE_GENERATED,
    SCHEDULE_PROPOSAL_SELECTED,
    SHIFT_ASSIGNMENT_CHANGED,
    SHIFT_PINNED,
    SHIFT_UNPINNED,
    SCHEDULE_PUBLISHED,
    SCHEDULE_REOPENED,
    EMPLOYEE_DEACTIVATED,
    EMPLOYEE_CREATED,
    USER_ROLE_CHANGED
}
