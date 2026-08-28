package com.aishiftplanner.scheduler.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@code @CreatedDate}/{@code @LastModifiedDate} on
 * {@link com.aishiftplanner.scheduler.shared.domain.BaseEntity} are populated automatically.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
