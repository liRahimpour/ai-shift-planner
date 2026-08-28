package com.aishiftplanner.scheduler.schedule.solver;

import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import java.time.LocalTime;

/**
 * One declared availability window, flattened for the solver.
 *
 * @param type AVAILABLE, PREFERRED or UNAVAILABLE
 * @param start null together with {@code end} means "the whole day"
 */
public record AvailabilitySlot(AvailabilityType type, LocalTime start, LocalTime end) {

    public boolean isWholeDay() {
        return start == null || end == null;
    }

    /** True if this window fully contains {@code [shiftStart, shiftEnd)}. */
    public boolean covers(LocalTime shiftStart, LocalTime shiftEnd) {
        if (isWholeDay()) {
            return true;
        }
        return !shiftStart.isBefore(start) && !shiftEnd.isAfter(end);
    }

    public boolean overlaps(LocalTime shiftStart, LocalTime shiftEnd) {
        if (isWholeDay()) {
            return true;
        }
        return shiftStart.isBefore(end) && shiftEnd.isAfter(start);
    }
}
