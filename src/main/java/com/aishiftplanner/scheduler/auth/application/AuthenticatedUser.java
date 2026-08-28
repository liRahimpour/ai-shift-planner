package com.aishiftplanner.scheduler.auth.application;

import com.aishiftplanner.scheduler.auth.domain.Role;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal.
 *
 * <p>Carries the tenant ({@code organizationId}) alongside the roles, because almost every
 * authorization question in this product is really two questions: "may this role do this?"
 * and "is this row in this user's organization?". Keeping both on the principal means the
 * second question can be answered without an extra database round trip on every request.
 *
 * <p>The password hash is deliberately not carried here — this principal is created from a
 * verified JWT, so there is nothing left to compare a password against, and not holding the
 * hash means it cannot accidentally end up in a log or a serialized response.
 */
public record AuthenticatedUser(
        UUID userId,
        UUID organizationId,
        String email,
        String displayName,
        Set<Role> roles,
        boolean active)
        implements UserDetails {

    public AuthenticatedUser {
        roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isManager() {
        return roles.contains(Role.SHIFT_MANAGER)
                || roles.contains(Role.LOCATION_MANAGER)
                || roles.contains(Role.ORG_ADMIN)
                || roles.contains(Role.SUPER_ADMIN);
    }

    /** True if the given row belongs to this principal's tenant. */
    public boolean ownsTenant(UUID candidateOrganizationId) {
        return organizationId.equals(candidateOrganizationId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();
    }

    @Override
    public String getPassword() {
        return null; // never held on an already-authenticated principal
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public static AuthenticatedUser of(com.aishiftplanner.scheduler.auth.domain.User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getOrganizationId(),
                user.getEmail(),
                user.fullName(),
                user.getRoles(),
                user.isActive());
    }

    /** Roles as plain strings, for embedding in a token claim. */
    public List<String> roleNames() {
        return roles.stream().map(Enum::name).toList();
    }
}
