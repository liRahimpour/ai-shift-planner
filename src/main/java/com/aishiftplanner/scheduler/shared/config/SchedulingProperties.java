package com.aishiftplanner.scheduler.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable planning limits.
 *
 * <p>These are deliberately <em>configuration</em>, not constants. German law happens to
 * require 11 hours of rest between shifts and caps a working day at 10 hours, but this
 * product is meant to run in other jurisdictions and under collective agreements with
 * stricter rules. Hardcoding "11" anywhere in the constraint code would make the solver
 * quietly wrong the first time someone deploys it outside Germany.
 *
 * @param minimumRestHoursBetweenShifts minimum gap between the end of one shift and the
 *     start of the next for the same employee
 * @param maximumShiftHoursPerDay maximum hours a single employee may work within one day
 * @param maximumConsecutiveWorkDays maximum number of consecutive days worked
 * @param solverSecondsSpentLimit wall-clock budget per planning run, per strategy
 */
@ConfigurationProperties(prefix = "app.scheduling")
public record SchedulingProperties(
        double minimumRestHoursBetweenShifts,
        double maximumShiftHoursPerDay,
        int maximumConsecutiveWorkDays,
        long solverSecondsSpentLimit) {

    public SchedulingProperties {
        if (minimumRestHoursBetweenShifts <= 0) {
            minimumRestHoursBetweenShifts = 11;
        }
        if (maximumShiftHoursPerDay <= 0) {
            maximumShiftHoursPerDay = 10;
        }
        if (maximumConsecutiveWorkDays <= 0) {
            maximumConsecutiveWorkDays = 6;
        }
        if (solverSecondsSpentLimit <= 0) {
            solverSecondsSpentLimit = 30;
        }
    }
}
