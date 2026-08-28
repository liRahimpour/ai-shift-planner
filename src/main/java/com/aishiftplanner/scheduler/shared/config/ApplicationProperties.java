package com.aishiftplanner.scheduler.shared.config;

import com.aishiftplanner.scheduler.ai.infrastructure.AiProperties;
import com.aishiftplanner.scheduler.auth.infrastructure.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the typed configuration-properties records used across the application. */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, SchedulingProperties.class, AiProperties.class})
public class ApplicationProperties {
}
