package com.aishiftplanner.scheduler.availability.domain;

/**
 * How an employee has marked a time window.
 *
 * <p>The distinction between {@link #UNAVAILABLE} and {@link #PREFERRED} is what separates a
 * hard constraint from a soft one in the solver: being unavailable is never overridden,
 * while a preference is traded off against fairness, cost and coverage.
 */
public enum AvailabilityType {

    /** Can work; no particular wish either way. */
    AVAILABLE,

    /** Can work and would like to. Rewarded by the soft constraints. */
    PREFERRED,

    /** Cannot work. Enforced as a hard constraint — never scheduled over. */
    UNAVAILABLE;

    public boolean isHardBlock() {
        return this == UNAVAILABLE;
    }

    public boolean isWish() {
        return this == PREFERRED;
    }
}
