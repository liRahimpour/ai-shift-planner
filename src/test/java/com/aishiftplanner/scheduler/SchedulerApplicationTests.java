package com.aishiftplanner.scheduler;

import com.aishiftplanner.scheduler.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: the full Spring context (web, JPA, Flyway, Security, Timefold, Spring AI)
 * must come up cleanly against a real PostgreSQL instance. If this fails, nothing else in
 * the application can be trusted, so it is deliberately the very first test written.
 */
class SchedulerApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Intentionally empty: @SpringBootTest already failed the test if the context
        // could not start.
    }
}
