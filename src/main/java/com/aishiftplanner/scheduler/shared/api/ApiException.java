package com.aishiftplanner.scheduler.shared.api;

import org.springframework.http.HttpStatus;

/**
 * Base type for exceptions that should be translated into the standard {@link ApiError}
 * response format by {@link GlobalExceptionHandler}.
 *
 * <p>Business/application code should throw a specific subclass (or this class directly
 * with an appropriate {@link ErrorCode}) instead of letting framework exceptions leak to
 * clients as opaque 500s.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    public static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.CONFLICT, message);
    }

    public static ApiException badRequest(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.BAD_REQUEST, message);
    }
}
