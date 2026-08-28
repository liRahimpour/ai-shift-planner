package com.aishiftplanner.scheduler.shared.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A concrete block of working time, resolved to actual local date-times.
 *
 * <p>This type exists because gastronomy shifts routinely cross midnight ("Bar 18:00–02:00"),
 * and a naive {@code (date, startTime, endTime)} triple gets that wrong in three separate
 * ways: the duration comes out negative, the shift appears to end before it starts, and rest
 * time to the next day's shift is computed from the wrong day. Resolving to
 * {@link LocalDateTime} once, at the boundary, means no downstream code has to remember the
 * special case.
 *
 * <p>Local, not zoned, on purpose: a shift that starts at 18:00 starts at 18:00 on both sides
 * of a DST change. The location's zone is applied only when a real instant is needed.
 */
public record TimeWindow(LocalDateTime start, LocalDateTime end) {

    /**
     * The end of a calendar day for availability purposes.
     *
     * <p>Deliberately 23:59 rather than {@link java.time.LocalTime#MAX}. When checking whether someone's
     * declared availability covers the same-day part of an overnight shift, the comparison is
     * against this value — and {@code LocalTime.MAX} is 23:59:59.999999999, so a perfectly
     * sensible "available until 23:59" would fail to cover it by a fraction of a second and
     * the person would be treated as unavailable for every bar shift. The rule this encodes is
     * simple and explainable to a user: <em>to be eligible for an overnight shift, declare
     * availability through to 23:59</em> (or the whole day).
     */
    public static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    public TimeWindow {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("A time window must end after it starts: " + start + " → " + end);
        }
    }

    /**
     * Builds a window from a shift's stored representation.
     *
     * @param crossesMidnight when true, {@code endTime} belongs to the following calendar day
     */
    public static TimeWindow of(LocalDate date, LocalTime startTime, LocalTime endTime, boolean crossesMidnight) {
        LocalDateTime start = LocalDateTime.of(date, startTime);
        LocalDateTime end = crossesMidnight
                ? LocalDateTime.of(date.plusDays(1), endTime)
                : LocalDateTime.of(date, endTime);
        return new TimeWindow(start, end);
    }

    /** True if two windows share any time at all — i.e. one employee cannot work both. */
    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    /** Gap between this window ending and {@code later} starting; negative if they overlap. */
    public Duration restBefore(TimeWindow later) {
        return Duration.between(end, later.start);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public double hours() {
        return duration().toMinutes() / 60.0;
    }

    public LocalDate startDate() {
        return start.toLocalDate();
    }

    /** True if any part of the window falls on {@code date}. */
    public boolean touches(LocalDate date) {
        return !start.toLocalDate().isAfter(date) && !end.toLocalDate().isBefore(date);
    }

    /**
     * True if the window ends at or after 22:00, or starts before 06:00 — the "evening or
     * night" bucket used by the fairness constraints. The threshold is a business definition
     * of an unpopular shift, not a legal one.
     */
    public boolean isEveningOrNight() {
        return end.toLocalTime().isAfter(LocalTime.of(21, 59))
                || end.toLocalDate().isAfter(start.toLocalDate())
                || start.toLocalTime().isBefore(LocalTime.of(6, 0));
    }
}
