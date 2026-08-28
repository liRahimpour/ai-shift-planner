package com.aishiftplanner.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the AI Shift Planner backend.
 *
 * <p>This is a modular monolith (see docs/adr/009-use-modular-monolith.md): one deployable
 * Spring Boot application organized by feature package, not a collection of microservices.
 */
@SpringBootApplication
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }
}
