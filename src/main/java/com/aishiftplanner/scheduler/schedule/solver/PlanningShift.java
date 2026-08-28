package com.aishiftplanner.scheduler.schedule.solver;

import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * A shift as the solver sees it: an immutable problem fact.
 *
 * @param requiredSkills skill id → how many of the assigned people must hold it
 * @param window resolved start/end, with midnight crossing already applied
 */
public record PlanningShift(
        UUID id,
        UUID locationId,
        UUID departmentId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean crossesMidnight,
        int requiredEmployees,
        int minimumEmployees,
        Map<UUID, Integer> requiredSkills,
        TimeWindow window) {

    public static PlanningShift from(com.aishiftplanner.scheduler.schedule.domain.Shift shift) {
        return new PlanningShift(
                shift.getId(),
                shift.getLocationId(),
                shift.getDepartmentId(),
                shift.getDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.isCrossesMidnight(),
                shift.getRequiredEmployees(),
                shift.getMinimumEmployees(),
                Map.copyOf(shift.getRequiredSkills()),
                shift.window());
    }

    public double durationHours() {
        return window.hours();
    }

    public boolean isWeekend() {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public boolean isEveningOrNight() {
        return window.isEveningOrNight();
    }

    /**
     * A closing shift is one that ends late enough to be the last of the day — the shift
     * nobody volunteers for, and therefore the one fairness has to watch most closely.
     */
    public boolean isClosing() {
        return crossesMidnight || endTime.isAfter(LocalTime.of(22, 30));
    }
}
