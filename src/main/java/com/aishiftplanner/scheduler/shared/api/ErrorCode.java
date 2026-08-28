package com.aishiftplanner.scheduler.shared.api;

/**
 * Stable, machine-readable error codes returned to API clients.
 *
 * <p>The string value (not the enum name) is part of the public API contract, so codes must
 * stay stable across releases. Add new codes here rather than throwing raw exception
 * messages back to clients.
 */
public enum ErrorCode {

    VALIDATION_FAILED,
    NOT_FOUND,
    ALREADY_EXISTS,
    FORBIDDEN,
    UNAUTHENTICATED,
    TENANT_MISMATCH,
    CONFLICT,
    OPTIMISTIC_LOCK_CONFLICT,
    AVAILABILITY_DEADLINE_PASSED,
    PLANNING_PERIOD_NOT_READY,
    SCHEDULING_CONFLICT,
    AI_TEMPORARILY_UNAVAILABLE,
    INTERNAL_ERROR
}
