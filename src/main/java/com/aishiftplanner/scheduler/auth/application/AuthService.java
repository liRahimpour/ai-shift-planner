package com.aishiftplanner.scheduler.auth.application;

import com.aishiftplanner.scheduler.auth.api.AuthDtos.CurrentUserResponse;
import com.aishiftplanner.scheduler.auth.api.AuthDtos.LoginRequest;
import com.aishiftplanner.scheduler.auth.api.AuthDtos.TokenResponse;
import com.aishiftplanner.scheduler.auth.domain.User;
import com.aishiftplanner.scheduler.auth.infrastructure.JwtService;
import com.aishiftplanner.scheduler.auth.infrastructure.UserRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email + password login, and refresh-token exchange. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * A bcrypt hash of a value nobody knows, used to keep the timing of a failed login for
     * an unknown email indistinguishable from one with a wrong password. Without it, an
     * attacker can enumerate valid accounts purely from response latency.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(request.email());

        // Always run a password comparison, even when the account does not exist.
        String hash = maybeUser.map(User::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        User user = maybeUser
                .filter(u -> passwordMatches && u.isActive())
                .orElseThrow(this::invalidCredentials);

        user.setLastLoginAt(Instant.now());
        // The email is logged, the password never is - not even a masked form of it.
        log.info("Successful login for user {} (organization {})", user.getId(), user.getOrganizationId());

        return buildTokens(AuthenticatedUser.of(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        AuthenticatedUser fromToken = jwtService.verifyRefreshToken(refreshToken)
                .orElseThrow(this::invalidCredentials);

        // Re-read the user so that a deactivation, a role change or a deletion since the
        // refresh token was issued takes effect immediately rather than at token expiry.
        User user = userRepository.findById(fromToken.userId())
                .filter(User::isActive)
                .orElseThrow(this::invalidCredentials);

        return buildTokens(AuthenticatedUser.of(user));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("User not found."));
        return toResponse(user);
    }

    private TokenResponse buildTokens(AuthenticatedUser user) {
        return new TokenResponse(
                jwtService.issueAccessToken(user),
                jwtService.issueRefreshToken(user),
                "Bearer",
                jwtService.accessTokenTtl().toSeconds(),
                new CurrentUserResponse(
                        user.userId(),
                        user.organizationId(),
                        user.email(),
                        firstNameOf(user.displayName()),
                        lastNameOf(user.displayName()),
                        user.roleNames()));
    }

    private static CurrentUserResponse toResponse(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getOrganizationId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles().stream().map(Enum::name).toList());
    }

    private ApiException invalidCredentials() {
        // Deliberately identical for "no such user", "wrong password" and "deactivated":
        // a more specific message would tell an attacker which emails are registered.
        return new ApiException(
                ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    private static String firstNameOf(String displayName) {
        int space = displayName == null ? -1 : displayName.indexOf(' ');
        return space < 0 ? (displayName == null ? "" : displayName) : displayName.substring(0, space);
    }

    private static String lastNameOf(String displayName) {
        int space = displayName == null ? -1 : displayName.indexOf(' ');
        return space < 0 ? "" : displayName.substring(space + 1);
    }
}
