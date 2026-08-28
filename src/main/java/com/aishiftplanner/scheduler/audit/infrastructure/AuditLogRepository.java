package com.aishiftplanner.scheduler.audit.infrastructure;

import com.aishiftplanner.scheduler.audit.domain.AuditLogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read and append only. No update or delete method is exposed here on purpose — see
 * {@link AuditLogEntry}.
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    List<AuditLogEntry> findAllByOrganizationIdOrderByOccurredAtDesc(UUID organizationId, Pageable pageable);

    List<AuditLogEntry> findAllByOrganizationIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
            UUID organizationId, String entityType, UUID entityId);
}
