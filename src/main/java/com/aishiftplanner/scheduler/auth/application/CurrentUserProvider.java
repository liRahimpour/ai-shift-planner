package com.aishiftplanner.scheduler.auth.application;

import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single place where application code asks "who is calling?".
 *
 * <p>Going through this component rather than reading {@link SecurityContextHolder}
 * directly in each service keeps the assumption in one testable place, and makes
 * {@link #requireTenant(UUID)} the obvious way to assert that a loaded row belongs to the
 * caller's organization.
 */
@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user
                ? Optional.of(user)
                : Optional.empty();
    }

    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new ApiException(
                ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required."));
    }

    /** @return the caller's organization id. */
    public UUID requireOrganizationId() {
        return require().organizationId();
    }

    /**
     * Asserts that a row from the database belongs to the caller's tenant.
     *
     * <p>Answers with {@code 404 NOT_FOUND} semantics at the call site rather than a
     * distinct "wrong tenant" error, so that probing ids cannot be used to discover which
     * ids exist in other organizations.
     */
    public void requireTenant(UUID rowOrganizationId) {
        if (!require().ownsTenant(rowOrganizationId)) {
            throw new ApiException(
                    ErrorCode.TENANT_MISMATCH, HttpStatus.NOT_FOUND, "Resource not found.");
        }
    }
}
