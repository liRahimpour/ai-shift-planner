package com.aishiftplanner.scheduler.schedule.api;

import com.aishiftplanner.scheduler.planning.domain.PlanningJob;
import com.aishiftplanner.scheduler.schedule.domain.PlanningStrategy;
import com.aishiftplanner.scheduler.schedule.domain.ScheduleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record PlanningJobResponse(
            UUID jobId,
            UUID planningPeriodId,
            PlanningJob.Status status,
            String progressNote,
            String failureReason,
            Instant startedAt,
            Instant finishedAt) {
    }

    /** The six numbers a manager compares proposals by, plus feasibility. */
    public record ScheduleMetricsResponse(
            BigDecimal totalStaffCost,
            BigDecimal preferenceSatisfaction,
            BigDecimal contractHoursDeviation,
            int unfilledPositions,
            BigDecimal overtimeHours,
            BigDecimal fairnessScore,
            long hardScore,
            long softScore,
            boolean feasible) {
    }

    public record ScheduleSummaryResponse(
            UUID id,
            UUID planningPeriodId,
            PlanningStrategy strategy,
            ScheduleStatus status,
            boolean selected,
            ScheduleMetricsResponse metrics) {
    }

    public record AssignmentResponse(
            UUID assignmentId,
            UUID shiftId,
            int slotIndex,
            UUID employeeId,
            String employeeName,
            boolean pinned) {
    }

    public record ShiftWithAssignmentsResponse(
            UUID shiftId,
            UUID departmentId,
            String departmentName,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            boolean crossesMidnight,
            int requiredEmployees,
            int minimumEmployees,
            List<AssignmentResponse> assignments) {
    }

    public record ScheduleDetailResponse(
            ScheduleSummaryResponse summary, List<ShiftWithAssignmentsResponse> shifts) {
    }

    /** Reassigns one seat. A null {@code employeeId} clears it. */
    public record ReassignRequest(UUID employeeId) {
    }

    public record PinRequest(boolean pinned) {
    }

    /**
     * The result of validating a manual edit.
     *
     * <p>Warnings are returned rather than the edit being blocked: a shift manager on the
     * floor knows things the system does not, and the product's job is to make the
     * consequence visible ("Anna would have only 9 hours' rest before her next shift"), not
     * to overrule the person accountable for the rota. Publishing is where the line is drawn.
     */
    public record ValidationResponse(boolean feasible, List<ValidationIssue> issues) {
    }

    public record ValidationIssue(String severity, String code, String message, UUID employeeId, UUID shiftId) {

        public static ValidationIssue hard(String code, String message, UUID employeeId, UUID shiftId) {
            return new ValidationIssue("HARD", code, message, employeeId, shiftId);
        }

        public static ValidationIssue warning(String code, String message, UUID employeeId, UUID shiftId) {
            return new ValidationIssue("WARNING", code, message, employeeId, shiftId);
        }
    }

    public record ReassignResponse(AssignmentResponse assignment, ValidationResponse validation) {
    }

    /** One employee's published shifts — the employee-facing view. */
    public record MyScheduleResponse(UUID employeeId, List<ShiftWithAssignmentsResponse> shifts) {
    }
}
