package com.aishiftplanner.scheduler.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * Base for entities that belong to exactly one tenant (organization).
 *
 * <p>Every such entity stores {@code organizationId} as a plain column rather than only as
 * a nested association. That is deliberate: it lets repositories filter by tenant in a
 * single indexed predicate on every query, which is what makes the isolation rule
 * ("organizations must never see another organization's data") cheap enough to apply
 * unconditionally instead of case by case.
 */
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }
}
