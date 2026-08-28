package com.aishiftplanner.scheduler.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline security configuration.
 *
 * <p>This is intentionally minimal for Phase 1: only actuator health/info/OpenAPI are public,
 * everything else requires authentication, and there are no sessions (the API is stateless -
 * see docs/adr, "stateless backend"). Full JWT authentication and role-based authorization
 * are added in Phase 3 ({@code auth} module); this class is extended, not replaced, there.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Delegating encoder defaulting to bcrypt ({bcrypt}) - modern, salted, adaptive
        // hashing with zero extra native dependencies. See docs/adr for the rationale.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless, token-based API - no CSRF cookies involved
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/**")
                        .permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
