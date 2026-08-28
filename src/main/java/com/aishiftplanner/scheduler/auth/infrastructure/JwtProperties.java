package com.aishiftplanner.scheduler.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings, bound from {@code app.jwt.*} (which in turn come from environment
 * variables — see application.yml and .env.example). Nothing here is hardcoded in business
 * code, so the same image runs in dev, staging and production with different secrets.
 *
 * @param secret signing key; must be at least 32 bytes for HS256 and must be overridden
 *     outside local development
 * @param accessTokenTtlMinutes short-lived access token lifetime
 * @param refreshTokenTtlDays refresh token lifetime
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessTokenTtlMinutes, long refreshTokenTtlDays) {

    public JwtProperties {
        if (accessTokenTtlMinutes <= 0) {
            accessTokenTtlMinutes = 30;
        }
        if (refreshTokenTtlDays <= 0) {
            refreshTokenTtlDays = 14;
        }
    }
}
