package com.aishiftplanner.scheduler.shared.api;

import com.aishiftplanner.scheduler.shared.observability.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the uniform {@link ApiError} response format (see section 38 of
 * the product brief). Nothing below leaks a raw stack trace, a SQL message, or an internal
 * class name to the client; those details go to the log, keyed by the same trace id the
 * client receives, so support can correlate a bug report with the exact server-side error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        String traceId = CorrelationIdFilter.currentOrNew();
        if (ex.getStatus().is5xxServerError()) {
            log.error("[{}] {} - {}", traceId, ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("[{}] {} - {}", traceId, ex.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(ex.getCode(), ex.getMessage(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String traceId = CorrelationIdFilter.currentOrNew();
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.validation("Validation failed", traceId, violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        String traceId = CorrelationIdFilter.currentOrNew();
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.validation("Validation failed", traceId, violations));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        String traceId = CorrelationIdFilter.currentOrNew();
        log.warn("[{}] optimistic locking conflict - {}", traceId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "This record was changed by someone else. Please reload and try again.",
                traceId));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        String traceId = CorrelationIdFilter.currentOrNew();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ErrorCode.FORBIDDEN, "You are not allowed to perform this action.", traceId));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        String traceId = CorrelationIdFilter.currentOrNew();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ErrorCode.UNAUTHENTICATED, "Authentication is required.", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        String traceId = CorrelationIdFilter.currentOrNew();
        log.error("[{}] Unexpected error", traceId, ex);
        return ResponseEntity.internalServerError().body(ApiError.of(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Reference: " + traceId,
                traceId));
    }
}
