package com.aishiftplanner.scheduler.planning.api;

import com.aishiftplanner.scheduler.planning.domain.PlanningPeriodStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PlanningDtos {

    private PlanningDtos() {
    }

    public record CreatePlanningPeriodRequest(
            @NotNull UUID locationId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull Instant availabilityDeadline) {
    }

    public record ChangeDeadlineRequest(@NotNull Instant availabilityDeadline) {
    }

    public record ChangeStatusRequest(@NotNull PlanningPeriodStatus status) {
    }

    public record PlanningPeriodResponse(
            UUID id,
            UUID organizationId,
            UUID locationId,
            LocalDate startDate,
            LocalDate endDate,
            Instant availabilityDeadline,
            PlanningPeriodStatus status,
            boolean deadlinePassed,
            UUID createdBy) {
    }

    /**
     * The numbers the manager dashboard leads with: how many people still owe an answer, and
     * how long they have. This is the difference between "start planning" being a guess and
     * being an informed decision.
     */
    public record PlanningPeriodSummaryResponse(
            PlanningPeriodResponse period,
            long totalActiveEmployees,
            long employeesWithSubmissions,
            long employeesMissing,
            long commentCount,
            long pendingInterpretationReviews) {
    }
}
