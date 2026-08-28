package com.aishiftplanner.scheduler.organization.domain;

import com.aishiftplanner.scheduler.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A tenant: the top of the hierarchy Organization → Location → Department → Employee.
 *
 * <p>Not a {@link com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity} because
 * the organization <em>is</em> the tenant boundary — its own id is what everything else
 * scopes to.
 */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** URL-safe stable identifier, unique across the platform (e.g. {@code restaurant-group-gmbh}). */
    @Column(name = "slug", nullable = false, length = 100, unique = true)
    private String slug;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Organization() {
        // for JPA
    }

    public Organization(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
