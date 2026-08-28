package com.aishiftplanner.scheduler.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the system clock as a bean.
 *
 * <p>Deadlines, rest-time checks and "is this period still open?" are the heart of this
 * product, and all of them are time-dependent. Injecting a {@link Clock} instead of calling
 * {@code Instant.now()} inline is what makes those rules testable at an exact moment —
 * one second before a deadline and one second after — rather than with sleeps and luck.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
