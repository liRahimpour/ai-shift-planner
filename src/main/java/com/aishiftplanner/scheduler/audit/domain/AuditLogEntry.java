package com.aishiftplanner.scheduler.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One append-only audit record.
 *
 * <p>Not a {@code BaseEntity}: an audit row has no {@code updatedAt} and no {@code @Version},
 * because it is never updated. There is intentionally no setter for anything and no update
 * or delete path anywhere in the application — a log you can edit is not evidence.
 */
@Entity
@Table(name = "audit_log_entries")
public class AuditLogEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /** Null for system-initiated actions (e.g. a scheduled archival job). */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 60, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    /**
     * Structured context (what changed, from what, to what) as JSON.
     *
     * <p>Must never contain credentials, tokens or password hashes — the writing service is
     * responsible for that, and the metadata builders in {@code AuditService} do not accept
     * arbitrary objects for exactly this reason.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false)
    private String metadata = "{}";

    /** Ties the entry to the request's correlation id, so logs and audit can be joined. */
    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected AuditLogEntry() {
        // for JPA
    }

    public AuditLogEntry(
            UUID organizationId,
            UUID actorUserId,
            AuditAction action,
            String entityType,
            UUID entityId,
            String metadataJson,
            String correlationId) {
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadataJson == null ? "{}" : metadataJson;
        this.correlationId = correlationId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getMetadata() {
        return metadata;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
