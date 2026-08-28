package com.aishiftplanner.scheduler.audit.application;

import com.aishiftplanner.scheduler.audit.domain.AuditAction;
import com.aishiftplanner.scheduler.audit.domain.AuditLogEntry;
import com.aishiftplanner.scheduler.audit.infrastructure.AuditLogRepository;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.shared.observability.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit entries.
 *
 * <p>Two decisions worth noting:
 *
 * <ul>
 *   <li><b>{@code REQUIRES_NEW}.</b> The audit write runs in its own transaction so a record
 *       of an attempted action survives even if the business transaction is later rolled
 *       back. Losing the audit trail exactly when something went wrong would be the worst
 *       possible failure mode for it.
 *   <li><b>Never throws.</b> A failure to write an audit row is logged loudly but does not
 *       fail the user's request — the alternative is an outage of the whole product because
 *       a logging table is full.
 * </ul>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserProvider currentUser;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditLogRepository auditLogRepository, CurrentUserProvider currentUser, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String entityType, UUID entityId, Map<String, Object> metadata) {
        try {
            AuthenticatedUser actor = currentUser.find().orElse(null);
            if (actor == null) {
                // Nothing to attribute the action to and no tenant to file it under. This
                // should not happen on an authenticated path; log it rather than guessing.
                log.warn("Skipping audit entry for {} - no authenticated actor in context", action);
                return;
            }
            auditLogRepository.save(new AuditLogEntry(
                    actor.organizationId(),
                    actor.userId(),
                    action,
                    entityType,
                    entityId,
                    toJson(metadata),
                    CorrelationIdFilter.currentOrNew()));
        } catch (RuntimeException ex) {
            log.error("Failed to write audit entry for action {} on {} {}", action, entityType, entityId, ex);
        }
    }

    /** Convenience for the very common "one before/after pair" case. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChange(
            AuditAction action, String entityType, UUID entityId, String field, Object before, Object after) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("field", field);
        metadata.put("before", before == null ? null : String.valueOf(before));
        metadata.put("after", after == null ? null : String.valueOf(after));
        record(action, entityType, entityId, metadata);
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize audit metadata; storing an empty object instead", ex);
            return "{}";
        }
    }
}
