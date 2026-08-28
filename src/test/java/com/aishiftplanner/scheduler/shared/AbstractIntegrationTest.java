package com.aishiftplanner.scheduler.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need the full Spring context talking to a real PostgreSQL
 * database (via Testcontainers), instead of a hand-rolled in-memory substitute.
 *
 * <p>We deliberately do NOT use H2 for integration tests: Postgres-specific behavior
 * (constraints, timezones, JSONB, etc.) has to be caught here, not in production. The
 * container is started once per JVM and shared across subclasses via Testcontainers' reuse
 * of the static {@code @Container} field pattern combined with Spring's context caching.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
}
