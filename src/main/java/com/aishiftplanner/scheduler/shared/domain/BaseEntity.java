package com.aishiftplanner.scheduler.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Common base for all persistent entities.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li><b>Application-assigned UUID ids.</b> The id exists the moment the object is
 *       constructed, so entities can be wired to each other before the transaction commits,
 *       and ids can be logged/correlated without a flush. It also avoids leaking row counts
 *       through sequential ids in a multi-tenant product.
 *   <li><b>{@code @Version}.</b> Optimistic locking is on by default for every entity, so
 *       two shift managers editing the same plan get a {@code 409 OPTIMISTIC_LOCK_CONFLICT}
 *       instead of one silently overwriting the other (product brief §78).
 *   <li><b>equals/hashCode by id.</b> Using a stable, pre-assigned id keeps entities usable
 *       in {@code Set}s before and after persisting - the classic JPA pitfall when
 *       identifiers are database-generated.
 * </ul>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        // Compare across proxies of the same entity type by effective class.
        return Objects.equals(id, that.id)
                && effectiveClass(this).equals(effectiveClass(that));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    private static Class<?> effectiveClass(Object entity) {
        return entity instanceof org.hibernate.proxy.HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : entity.getClass();
    }
}
