package com.aishiftplanner.scheduler.organization.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A work area within a location — "Küche", "Theke", "Bar", "Backstube", "Verkauf", …
 *
 * <p>Departments are stored as data, never as an enum: a bakery, a cocktail bar and a
 * quick-service chain organize their floors completely differently, and none of them should
 * need a code change to model that.
 */
@Entity
@Table(name = "departments")
public class Department extends TenantScopedEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Department() {
        // for JPA
    }

    public Department(UUID organizationId, UUID locationId, String name) {
        setOrganizationId(organizationId);
        this.locationId = locationId;
        this.name = name;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
