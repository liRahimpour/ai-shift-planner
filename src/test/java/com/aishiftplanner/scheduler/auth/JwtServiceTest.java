package com.aishiftplanner.scheduler.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.domain.Role;
import com.aishiftplanner.scheduler.auth.infrastructure.JwtProperties;
import com.aishiftplanner.scheduler.auth.infrastructure.JwtService;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256!!";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 30, 14));

    private static AuthenticatedUser sampleUser() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "anna@example.com",
                "Anna Beispiel",
                EnumSet.of(Role.EMPLOYEE, Role.SHIFT_MANAGER),
                true);
    }

    @Test
    void accessTokenRoundTripsAllClaims() {
        AuthenticatedUser user = sampleUser();

        Optional<AuthenticatedUser> parsed = jwtService.verifyAccessToken(jwtService.issueAccessToken(user));

        assertThat(parsed).isPresent();
        assertThat(parsed.get().userId()).isEqualTo(user.userId());
        assertThat(parsed.get().organizationId()).isEqualTo(user.organizationId());
        assertThat(parsed.get().email()).isEqualTo("anna@example.com");
        assertThat(parsed.get().displayName()).isEqualTo("Anna Beispiel");
        assertThat(parsed.get().roles()).containsExactlyInAnyOrder(Role.EMPLOYEE, Role.SHIFT_MANAGER);
    }

    @Test
    void refreshTokenIsNotAcceptedAsAnAccessToken() {
        // Otherwise a long-lived refresh token would work as a long-lived API credential,
        // silently defeating the point of short access-token lifetimes.
        String refreshToken = jwtService.issueRefreshToken(sampleUser());

        assertThat(jwtService.verifyAccessToken(refreshToken)).isEmpty();
        assertThat(jwtService.verifyRefreshToken(refreshToken)).isPresent();
    }

    @Test
    void accessTokenIsNotAcceptedAsARefreshToken() {
        String accessToken = jwtService.issueAccessToken(sampleUser());

        assertThat(jwtService.verifyRefreshToken(accessToken)).isEmpty();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        JwtService attacker = new JwtService(
                new JwtProperties("a-completely-different-secret-of-sufficient-length", 30, 14));
        String forged = attacker.issueAccessToken(sampleUser());

        assertThat(jwtService.verifyAccessToken(forged)).isEmpty();
    }

    @Test
    void garbageTokenIsRejectedWithoutThrowing() {
        assertThat(jwtService.verifyAccessToken("not-a-jwt")).isEmpty();
        assertThat(jwtService.verifyAccessToken("")).isEmpty();
        assertThat(jwtService.verifyAccessToken("a.b.c")).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        // Flipping a character in the payload segment must invalidate the signature; if this
        // ever passed, a client could promote itself to ORG_ADMIN by editing its own token.
        String token = jwtService.issueAccessToken(sampleUser());
        String[] parts = token.split("\\.");
        String payload = parts[1];
        String tamperedPayload =
                payload.substring(0, payload.length() - 1) + (payload.endsWith("A") ? "B" : "A");
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(jwtService.verifyAccessToken(tampered)).isEmpty();
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        // Fail fast: a weak signing key must break the deployment, not silently weaken every
        // token the system ever issues.
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 30, 14)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
