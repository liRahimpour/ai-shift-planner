package com.aishiftplanner.scheduler.ai.infrastructure;

import com.aishiftplanner.scheduler.ai.domain.LocalAiClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports AI reachability at {@code /actuator/health}.
 *
 * <p>Registered as a plain {@link HealthIndicator}, deliberately <b>not</b> as a readiness
 * contributor. A pod whose Ollama sidecar is down must still take traffic: scheduling,
 * availability and publishing all work without it. Wiring this into readiness would let an
 * optional component roll the entire application out of the load balancer — turning a
 * degraded feature into an outage.
 */
@Component("localAi")
public class AiHealthIndicator implements HealthIndicator {

    private final LocalAiClient localAiClient;
    private final AiProperties properties;

    public AiHealthIndicator(LocalAiClient localAiClient, AiProperties properties) {
        this.localAiClient = localAiClient;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.enabled()) {
            return Health.up()
                    .withDetail("state", "DISABLED")
                    .withDetail("note", "AI features are switched off by configuration.")
                    .build();
        }

        boolean reachable = localAiClient.isAvailable();

        return (reachable ? Health.up() : Health.status("DEGRADED"))
                .withDetail(
                        "state",
                        reachable
                                ? "AVAILABLE"
                                : "AI_TEMPORARILY_UNAVAILABLE")
                .withDetail("model", properties.model())
                .withDetail("baseUrl", properties.baseUrl())
                .build();
    }
}