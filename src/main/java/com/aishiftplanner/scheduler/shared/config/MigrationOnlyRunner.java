package com.aishiftplanner.scheduler.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Lets the same image run as a migration Job: apply Flyway migrations, then exit 0.
 *
 * <p>Activated with {@code --app.migration-only=true} by the Helm pre-upgrade hook. Using the
 * application's own image rather than a standalone Flyway image guarantees the scripts are
 * exactly the ones this version of the code expects — a separate migration image is one more
 * artefact that can silently be a version behind, and a schema one version behind the code is
 * precisely the failure this Job exists to prevent.
 *
 * <p>By the time this runs, Spring Boot's Flyway auto-configuration has already migrated the
 * database as part of context startup. This bean's job is simply to stop the process
 * afterwards instead of sitting there as a server.
 */
@Component
@ConditionalOnProperty(name = "app.migration-only", havingValue = "true")
public class MigrationOnlyRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationOnlyRunner.class);

    private final ApplicationContext applicationContext;

    public MigrationOnlyRunner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Migration-only mode: schema migration completed, shutting down.");
        // Exit code 0 so Kubernetes records the Job as succeeded and Helm continues the
        // release. A failed migration never reaches here: the context fails to start, the
        // pod exits non-zero, and the Helm hook aborts the upgrade before any new
        // application pod is created.
        System.exit(org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0));
    }
}
