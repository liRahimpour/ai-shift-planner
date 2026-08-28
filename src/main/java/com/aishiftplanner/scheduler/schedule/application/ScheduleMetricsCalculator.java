package com.aishiftplanner.scheduler.schedule.application;

import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import com.aishiftplanner.scheduler.schedule.solver.AvailabilitySlot;
import com.aishiftplanner.scheduler.schedule.solver.PlanningEmployee;
import com.aishiftplanner.scheduler.schedule.solver.ShiftSchedule;
import com.aishiftplanner.scheduler.schedule.solver.ShiftSlot;
import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Derives the six numbers a manager compares proposals by.
 *
 * <p>These are computed from the solved plan, not read off the solver score. A score is a
 * single number tuned for search; "7.820 € staff cost, 91% wishes honoured, 0 unfilled" is
 * what a human actually decides on. Keeping them separate also means a change to constraint
 * weights never silently changes the reported cost.
 */
@Component
public class ScheduleMetricsCalculator {

    /**
     * @param totalStaffCost gross wage cost of the whole plan
     * @param preferenceSatisfaction share of PREFERRED windows actually used, 0–100
     * @param contractHoursDeviation mean absolute deviation from contract hours, as a percentage
     * @param unfilledPositions seats with nobody assigned
     * @param overtimeHours hours beyond contract hours, summed across employees
     * @param fairnessScore 0–100; 100 means unpopular shifts are spread perfectly evenly
     */
    public record Metrics(
            BigDecimal totalStaffCost,
            BigDecimal preferenceSatisfaction,
            BigDecimal contractHoursDeviation,
            int unfilledPositions,
            BigDecimal overtimeHours,
            BigDecimal fairnessScore) {
    }

    public Metrics calculate(ShiftSchedule solution) {
        Map<UUID, Double> hoursByEmployee = new LinkedHashMap<>();
        Map<UUID, Integer> unpopularShiftsByEmployee = new LinkedHashMap<>();
        BigDecimal cost = BigDecimal.ZERO;
        int unfilled = 0;
        int preferredWindowsHonoured = 0;

        for (ShiftSlot slot : solution.getSlots()) {
            if (!slot.isFilled()) {
                unfilled++;
                continue;
            }
            PlanningEmployee employee = slot.getEmployee();
            double hours = slot.durationHours();

            hoursByEmployee.merge(employee.id(), hours, Double::sum);
            cost = cost.add(employee.costFor(hours));

            if (slot.getShift().isWeekend()
                    || slot.getShift().isEveningOrNight()
                    || slot.getShift().isClosing()) {
                unpopularShiftsByEmployee.merge(employee.id(), 1, Integer::sum);
            }
            if (fallsInsideAPreferredWindow(slot)) {
                preferredWindowsHonoured++;
            }
        }

        int totalPreferredWindows = countPreferredWindows(solution);

        return new Metrics(
                cost.setScale(2, RoundingMode.HALF_UP),
                percentage(preferredWindowsHonoured, totalPreferredWindows),
                contractDeviationPercentage(solution, hoursByEmployee),
                unfilled,
                overtimeHours(solution, hoursByEmployee),
                fairness(unpopularShiftsByEmployee, solution.getEmployees().size()));
    }

    private boolean fallsInsideAPreferredWindow(ShiftSlot slot) {
        Set<AvailabilitySlot> declared =
                slot.getEmployee().availabilityByDate().get(slot.getShift().date());
        if (declared == null) {
            return false;
        }
        LocalTime start = slot.getShift().startTime();
        LocalTime end = slot.getShift().crossesMidnight() ? TimeWindow.END_OF_DAY : slot.getShift().endTime();
        return declared.stream()
                .anyMatch(a -> a.type() == AvailabilityType.PREFERRED && a.covers(start, end));
    }

    private int countPreferredWindows(ShiftSchedule solution) {
        return (int) solution.getEmployees().stream()
                .flatMap(e -> e.availabilityByDate().values().stream())
                .flatMap(Set::stream)
                .filter(a -> a.type() == AvailabilityType.PREFERRED)
                .count();
    }

    /**
     * Mean absolute deviation from contract hours, as a percentage of contract hours.
     *
     * <p>Employees with no contract hours (casual staff) are excluded from the mean rather
     * than counted as 0% deviation. Including them would let a business hiring mostly casuals
     * report a flattering figure that says nothing about how well it is treating the people
     * who actually have contracted hours.
     */
    private BigDecimal contractDeviationPercentage(
            ShiftSchedule solution, Map<UUID, Double> hoursByEmployee) {
        double totalDeviationPercent = 0;
        int counted = 0;
        double weeks = solution.weeksInPeriod();

        for (PlanningEmployee employee : solution.getEmployees()) {
            double target = employee.contractHoursPerWeek() * weeks;
            if (target <= 0) {
                continue;
            }
            double actual = hoursByEmployee.getOrDefault(employee.id(), 0.0);
            totalDeviationPercent += Math.abs(actual - target) / target * 100.0;
            counted++;
        }
        if (counted == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(totalDeviationPercent / counted).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal overtimeHours(ShiftSchedule solution, Map<UUID, Double> hoursByEmployee) {
        double weeks = solution.weeksInPeriod();
        double overtime = 0;
        for (PlanningEmployee employee : solution.getEmployees()) {
            double target = employee.contractHoursPerWeek() * weeks;
            double actual = hoursByEmployee.getOrDefault(employee.id(), 0.0);
            if (target > 0 && actual > target) {
                overtime += actual - target;
            }
        }
        return BigDecimal.valueOf(overtime).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fairness as 100 minus the normalized spread of unpopular shifts.
     *
     * <p>Uses the coefficient of variation rather than a raw standard deviation so that the
     * figure is comparable between a five-person café and a forty-person restaurant — an
     * absolute spread of 2 means something very different in each.
     */
    private BigDecimal fairness(Map<UUID, Integer> unpopularByEmployee, int employeeCount) {
        if (employeeCount == 0 || unpopularByEmployee.isEmpty()) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        double[] counts = new double[employeeCount];
        int index = 0;
        for (Integer count : unpopularByEmployee.values()) {
            if (index < counts.length) {
                counts[index++] = count;
            }
        }
        double mean = java.util.Arrays.stream(counts).average().orElse(0);
        if (mean == 0) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        double variance = java.util.Arrays.stream(counts)
                .map(c -> (c - mean) * (c - mean))
                .average()
                .orElse(0);
        double coefficientOfVariation = Math.sqrt(variance) / mean;
        double score = Math.max(0, 100 - coefficientOfVariation * 100);
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(int part, int total) {
        if (total == 0) {
            // No preferences expressed means none were disappointed. Reporting 0% here would
            // make a team that simply did not state wishes look badly served.
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }
}
