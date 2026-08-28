package com.aishiftplanner.scheduler.employee.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A qualification an employee can hold and a shift can require — {@code BAR},
 * {@code KITCHEN}, {@code COUNTER}, {@code CASH_REGISTER}, {@code SHIFT_LEAD},
 * {@code OPENING}, {@code CLOSING}, {@code COFFEE_MACHINE}, {@code FOOD_PREPARATION}, …
 *
 * <p>The catalogue is per organization and extensible at runtime. The seed data ships the
 * common gastronomy skills above as rows, not as an enum, so an operator can add
 * {@code COCKTAIL_LEAD} or {@code HACCP_OFFICER} without a release.
 */
@Entity
@Table(name = "skills")
public class Skill extends TenantScopedEntity {

    /** Stable machine-readable code, uppercase snake case, unique within the organization. */
    @Column(name = "code", nullable = false, length = 60)
    private String code;

    /** Human-readable label shown in the UI (may be localized by the operator). */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Skill() {
        // for JPA
    }

    public Skill(UUID organizationId, String code, String name) {
        setOrganizationId(organizationId);
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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
