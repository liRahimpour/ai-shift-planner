package com.aishiftplanner.scheduler.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI-layer settings, all sourced from environment variables.
 *
 * @param enabled master switch; when false the application runs with AI features reporting
 *     {@code AI_TEMPORARILY_UNAVAILABLE} and everything else working normally
 * @param requestTimeoutSeconds how long to wait for the model before giving up — kept short,
 *     because a manager waiting on a chat answer would rather be told the AI is unavailable
 *     than watch a spinner
 * @param maxToolCallsPerConversation bounds the tool-calling loop so a confused model cannot
 *     spin indefinitely against the database
 * @param minimumConfidenceForAutoApply interpretations below this stay in the review queue
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String model,
        double temperature,
        int requestTimeoutSeconds,
        int maxToolCallsPerConversation,
        double minimumConfidenceForAutoApply) {

    public AiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }
        if (model == null || model.isBlank()) {
            model = "llama3.1";
        }
        if (temperature < 0) {
            // Low but non-zero: this product wants consistent extraction and phrasing, not
            // creative variety.
            temperature = 0.2;
        }
        if (requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 30;
        }
        if (maxToolCallsPerConversation <= 0) {
            maxToolCallsPerConversation = 5;
        }
        if (minimumConfidenceForAutoApply <= 0 || minimumConfidenceForAutoApply > 1) {
            minimumConfidenceForAutoApply = 0.85;
        }
    }
}
