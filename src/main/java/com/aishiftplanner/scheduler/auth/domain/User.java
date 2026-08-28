package com.aishiftplanner.scheduler.auth.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A login identity.
 *
 * <p>Kept separate from {@link com.aishiftplanner.scheduler.employee.domain.Employee} on
 * purpose: an org admin or accountant may need an account without being schedulable staff,
 * and an employee's historical schedule data must survive their login being deactivated.
 */
@Entity
@Table(name = "users")
public class User extends TenantScopedEntity {

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    /**
     * Hash produced by the delegating password encoder (bcrypt by default). The plaintext
     * password is never stored, never logged, and never returned by any API.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected User() {
        // for JPA
    }

    public User(UUID organizationId, String email, String passwordHash, String firstName, String lastName) {
        setOrganizationId(organizationId);
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /**
     * @return true if this user holds any role that may plan or edit schedules. Convenience
     *     for read paths; write paths still go through explicit method security.
     */
    public boolean isManager() {
        return roles.contains(Role.SHIFT_MANAGER)
                || roles.contains(Role.LOCATION_MANAGER)
                || roles.contains(Role.ORG_ADMIN)
                || roles.contains(Role.SUPER_ADMIN);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }
}
