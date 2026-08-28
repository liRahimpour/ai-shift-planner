package com.aishiftplanner.scheduler.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request/response payloads for {@code /api/v1/auth}. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 200) String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /**
     * @param expiresInSeconds lifetime of {@code accessToken}, so clients can refresh
     *     proactively instead of waiting for a 401
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            CurrentUserResponse user) {
    }

    /** Never includes a password hash or any other credential material. */
    public record CurrentUserResponse(
            UUID id,
            UUID organizationId,
            String email,
            String firstName,
            String lastName,
            List<String> roles) {
    }
}
