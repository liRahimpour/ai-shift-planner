package com.aishiftplanner.scheduler.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies that the Kubernetes-facing health probes are publicly reachable and report
 * a healthy application once Spring Boot and PostgreSQL are ready.
 *
 * <p>If these endpoints are broken, Kubernetes readiness and liveness probes would also
 * fail and the application could either receive no traffic or continuously restart.
 */
@AutoConfigureTestRestTemplate
class ActuatorHealthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointIsPublicAndUp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        "/actuator/health",
                        String.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .contains("\"status\":\"UP\"");
    }

    @Test
    void livenessProbeIsUp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        "/actuator/health/liveness",
                        String.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void readinessProbeIsUp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        "/actuator/health/readiness",
                        String.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}