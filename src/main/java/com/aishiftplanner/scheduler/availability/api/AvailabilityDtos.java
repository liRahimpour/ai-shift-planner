package com.aishiftplanner.scheduler.availability.api;

import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import com.aishiftplanner.scheduler.availability.domain.CommentInterpretation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AvailabilityDtos {

    private AvailabilityDtos() {
    }

    /** One declared window. Null times mean "the whole day". */
    public record AvailabilityWindowRequest(
            @NotNull LocalDate date,
            @NotNull AvailabilityType availabilityType,
            LocalTime startTime,
            LocalTime endTime) {
    }

    /**
     * A complete submission for one employee and one planning period.
     *
     * <p>Deliberately a full replace rather than per-window CRUD: an employee thinks in terms
     * of "here is my week", and replacing the set atomically avoids the half-updated states
     * that per-row editing produces when a request fails midway.
     */
    public record SubmitAvailabilityRequest(
            @NotEmpty @Valid List<AvailabilityWindowRequest> windows) {
    }

    public record AvailabilityResponse(
            UUID id,
            UUID planningPeriodId,
            UUID employeeId,
            LocalDate date,
            AvailabilityType availabilityType,
            LocalTime startTime,
            LocalTime endTime) {
    }

    public record SubmitCommentRequest(@NotNull @Size(min = 1, max = 2000) String text) {
    }

    public record CommentResponse(
            UUID id,
            UUID planningPeriodId,
            UUID employeeId,
            String originalText,
            Instant createdAt,
            List<InterpretationResponse> interpretations) {
    }

    public record InterpretationResponse(
            UUID id,
            UUID commentId,
            LocalDate interpretedDate,
            AvailabilityType availabilityType,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            boolean hardConstraint,
            BigDecimal confidence,
            CommentInterpretation.Source source,
            String interpretation,
            CommentInterpretation.ReviewStatus reviewStatus,
            boolean needsReview) {
    }

    public record ReviewInterpretationRequest(@NotNull Boolean accept) {
    }
}
