package com.aishiftplanner.scheduler.auth.infrastructure;

import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the stateless access/refresh tokens used by the API.
 *
 * <p>Why stateless JWTs for the MVP: the backend must run as several interchangeable pods
 * with no sticky sessions and no shared session store (cloud-native requirement), and a
 * signed token is the smallest thing that satisfies that without adding Redis. The trade-off
 * — a token cannot be revoked before it expires — is bounded by keeping access tokens
 * short-lived (30 minutes by default) and is called out in the README's known limitations.
 * The whole class sits behind a narrow interface surface so swapping in Keycloak/Auth0 later
 * touches this file and the filter, not the rest of the application.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_ORGANIZATION = "org";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Fail fast and loudly: a short key silently weakens every token in the system.
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) must be at least 32 bytes long for HS256.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtl = Duration.ofMinutes(properties.accessTokenTtlMinutes());
        this.refreshTokenTtl = Duration.ofDays(properties.refreshTokenTtlDays());
    }

    public String issueAccessToken(AuthenticatedUser user) {
        return issue(user, TOKEN_TYPE_ACCESS, accessTokenTtl);
    }

    public String issueRefreshToken(AuthenticatedUser user) {
        return issue(user, TOKEN_TYPE_REFRESH, refreshTokenTtl);
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    private String issue(AuthenticatedUser user, String tokenType, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.userId().toString())
                .claim(CLAIM_ORGANIZATION, user.organizationId().toString())
                .claim(CLAIM_ROLES, user.roleNames())
                .claim(CLAIM_NAME, user.displayName())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies a token's signature and expiry and rebuilds the principal from its claims.
     *
     * <p>Returns {@link Optional#empty()} rather than throwing for any invalid token: an
     * expired or forged token is an expected, routine condition on a public endpoint, not
     * an exceptional one, and treating it as an exception invites noisy logs that bury real
     * problems. The token itself is never logged.
     */
    public Optional<AuthenticatedUser> verifyAccessToken(String token) {
        return verify(token, TOKEN_TYPE_ACCESS);
    }

    public Optional<AuthenticatedUser> verifyRefreshToken(String token) {
        return verify(token, TOKEN_TYPE_REFRESH);
    }

    private Optional<AuthenticatedUser> verify(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                // A refresh token must not be usable as an access token, and vice versa.
                return Optional.empty();
            }

            UUID userId = UUID.fromString(claims.getSubject());
            UUID organizationId = UUID.fromString(claims.get(CLAIM_ORGANIZATION, String.class));
            Set<Role> roles = parseRoles(claims);
            String displayName = claims.get(CLAIM_NAME, String.class);

            return Optional.of(new AuthenticatedUser(
                    userId,
                    organizationId,
                    claims.get(CLAIM_EMAIL, String.class),
                    displayName,
                    roles,
                    true));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected token: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Set<Role> parseRoles(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        if (!(raw instanceof List<?> list)) {
            return EnumSet.noneOf(Role.class);
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (Object element : list) {
            try {
                roles.add(Role.valueOf(String.valueOf(element)));
            } catch (IllegalArgumentException ignored) {
                // An unknown role name in a token grants nothing; drop it silently rather
                // than failing the whole token, so removing a role from the enum does not
                // lock out every user who still holds an old token.
            }
        }
        return roles;
    }
}
