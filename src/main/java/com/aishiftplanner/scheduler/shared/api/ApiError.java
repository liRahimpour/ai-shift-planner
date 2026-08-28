package com.aishiftplanner.scheduler.shared.api;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error response body for every {@code /api/v1/**} endpoint.
 *
 * <pre>{@code
 * {
 *   "code": "AVAILABILITY_DEADLINE_PASSED",
 *   "message": "Die Verfügbarkeit kann nicht mehr verändert werden.",
 *   "timestamp": "2026-08-28T10:15:30Z",
 *   "traceId": "a1b2c3d4e5f6"
 * }
 * }</pre>
 */
public record ApiError(
        ErrorCode code,
        String message,
        Instant timestamp,
        String traceId,
        List<FieldViolation> violations) {

    public static ApiError of(ErrorCode code, String message, String traceId) {
        return new ApiError(code, message, Instant.now(), traceId, List.of());
    }

    public static ApiError validation(String message, String traceId, List<FieldViolation> violations) {
        return new ApiError(ErrorCode.VALIDATION_FAILED, message, Instant.now(), traceId, violations);
    }

    public record FieldViolation(String field, String message) {
    }
}
