package com.aishiftplanner.scheduler.auth.api;

import com.aishiftplanner.scheduler.auth.api.AuthDtos.CurrentUserResponse;
import com.aishiftplanner.scheduler.auth.api.AuthDtos.LoginRequest;
import com.aishiftplanner.scheduler.auth.api.AuthDtos.RefreshRequest;
import com.aishiftplanner.scheduler.auth.api.AuthDtos.TokenResponse;
import com.aishiftplanner.scheduler.auth.application.AuthService;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Email/password login and token refresh")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for an access/refresh token pair")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated"),
        @ApiResponse(responseCode = "401", description = "Invalid email or password"),
        @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a fresh access/refresh token pair")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Refreshed"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user's profile and roles")
    public CurrentUserResponse me() {
        return authService.currentUser(currentUserProvider.require());
    }
}
