package com.aishiftplanner.scheduler.ai.domain;

import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the local model cannot be reached or returned something unusable.
 *
 * <p>Maps to {@code 503 AI_TEMPORARILY_UNAVAILABLE} — a distinct, honest status rather than a
 * generic 500. The whole point of the architecture (ADR-003) is that this is a degraded
 * feature, not a broken application: availability, planning, editing and publishing all keep
 * working while this is being thrown.
 */
public class AiUnavailableException extends ApiException {

    public AiUnavailableException(String message) {
        super(ErrorCode.AI_TEMPORARILY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public static AiUnavailableException notReachable() {
        return new AiUnavailableException(
                "The local AI service is not reachable right now. Scheduling is unaffected; "
                        + "AI features will work again once it is back.");
    }

    public static AiUnavailableException unusableResponse() {
        return new AiUnavailableException(
                "The local AI service returned a response that could not be understood.");
    }
}
