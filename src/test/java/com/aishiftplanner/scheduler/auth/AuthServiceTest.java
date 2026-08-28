package com.aishiftplanner.scheduler.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.aishiftplanner.scheduler.auth.api.AuthDtos.LoginRequest;
import com.aishiftplanner.scheduler.auth.api.AuthDtos.TokenResponse;
import com.aishiftplanner.scheduler.auth.application.AuthService;
import com.aishiftplanner.scheduler.auth.domain.Role;
import com.aishiftplanner.scheduler.auth.domain.User;
import com.aishiftplanner.scheduler.auth.infrastructure.JwtProperties;
import com.aishiftplanner.scheduler.auth.infrastructure.JwtService;
import com.aishiftplanner.scheduler.auth.infrastructure.UserRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private User activeUser;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtService jwtService = new JwtService(
                new JwtProperties("unit-test-secret-that-is-long-enough-for-hs256!!", 30, 14));
        authService = new AuthService(userRepository, passwordEncoder, jwtService);

        activeUser = new User(
                ORGANIZATION_ID,
                "anna@example.com",
                passwordEncoder.encode(PASSWORD),
                "Anna",
                "Beispiel");
        activeUser.setRoles(EnumSet.of(Role.EMPLOYEE));
    }

    @Test
    void loginWithCorrectCredentialsReturnsTokens() {
        when(userRepository.findByEmailIgnoreCase("anna@example.com")).thenReturn(Optional.of(activeUser));

        TokenResponse response = authService.login(new LoginRequest("anna@example.com", PASSWORD));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(response.user().roles()).containsExactly("EMPLOYEE");
    }

    @Test
    void loginRecordsTheLoginTimestamp() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(activeUser));

        authService.login(new LoginRequest("anna@example.com", PASSWORD));

        assertThat(activeUser.getLastLoginAt()).isNotNull();
    }

    @Test
    void loginWithWrongPasswordFails() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("anna@example.com", "wrong-password")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void loginForUnknownEmailFailsWithTheSameMessageAsAWrongPassword() {
        // Identical wording matters: a distinct "unknown user" message would let anyone
        // enumerate which email addresses have accounts on the platform.
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("anna@example.com")).thenReturn(Optional.of(activeUser));

        String unknownEmailMessage = messageOf(() ->
                authService.login(new LoginRequest("ghost@example.com", PASSWORD)));
        String wrongPasswordMessage = messageOf(() ->
                authService.login(new LoginRequest("anna@example.com", "wrong-password")));

        assertThat(unknownEmailMessage).isEqualTo(wrongPasswordMessage);
    }

    @Test
    void deactivatedUserCannotLogIn() {
        activeUser.setActive(false);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("anna@example.com", PASSWORD)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refreshRejectsAnAccessToken() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(activeUser));
        TokenResponse tokens = authService.login(new LoginRequest("anna@example.com", PASSWORD));

        assertThatThrownBy(() -> authService.refresh(tokens.accessToken()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refreshFailsOnceTheUserHasBeenDeactivated() {
        // The refresh path re-reads the user precisely so that a deactivation takes effect
        // immediately, rather than at the refresh token's natural expiry days later.
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(activeUser));
        TokenResponse tokens = authService.login(new LoginRequest("anna@example.com", PASSWORD));

        activeUser.setActive(false);
        when(userRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
                .isInstanceOf(ApiException.class);
    }

    private static String messageOf(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected the call to fail");
        } catch (ApiException ex) {
            return ex.getMessage();
        }
    }
}
