package com.aishiftplanner.scheduler.employee.domain;

/**
 * Contract category of an employee.
 *
 * <p>Deliberately carries <em>no</em> labour-law rules. It is tempting to attach "minijob =
 * max 10 hours/week" or "working student = max 20 hours during term" to these constants,
 * but those limits differ by country, by collective agreement, and change over time.
 * Numeric limits live on the {@link Employee} record and in configurable planning
 * constraints instead, so the same code can serve a German Minijob and its equivalent
 * elsewhere.
 */
public enum EmploymentType {
    FULL_TIME,
    PART_TIME,
    MINIJOB,
    WORKING_STUDENT,
    TEMPORARY,
    OTHER
}
