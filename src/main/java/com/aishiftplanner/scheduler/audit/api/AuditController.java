package com.aishiftplanner.scheduler.audit.api;

import com.aishiftplanner.scheduler.audit.domain.AuditAction;
import com.aishiftplanner.scheduler.audit.domain.AuditLogEntry;
import com.aishiftplanner.scheduler.audit.infrastructure.AuditLogRepository;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read access to the audit trail.
 *
 * <p>Read-only by construction — there is no write, update or delete endpoint anywhere for
 * this data, and the repository exposes none either. A log that can be edited through the
 * API it audits is not evidence.
 */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Audit", description = "Append-only record of consequential actions")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserProvider currentUser;

    public AuditController(AuditLogRepository auditLogRepository, CurrentUserProvider currentUser) {
        this.auditLogRepository = auditLogRepository;
        this.currentUser = currentUser;
    }

    public record AuditEntryResponse(
            UUID id,
            UUID actorUserId,
            AuditAction action,
            String entityType,
            UUID entityId,
            String metadata,
            String correlationId,
            Instant occurredAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Recent audit entries for the caller's organization, newest first")
    public List<AuditEntryResponse> recent(@RequestParam(defaultValue = "50") int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        return auditLogRepository
                .findAllByOrganizationIdOrderByOccurredAtDesc(
                        currentUser.requireOrganizationId(), PageRequest.of(0, size))
                .stream()
                .map(AuditController::toResponse)
                .toList();
    }

    @GetMapping("/entity")
    @Transactional(readOnly = true)
    @Operation(
            summary = "The full history of one entity",
            description = "Answers 'who changed this, when, and from what' for a single planning "
                    + "period, schedule or assignment.")
    public List<AuditEntryResponse> forEntity(
            @RequestParam String entityType, @RequestParam UUID entityId) {
        return auditLogRepository
                .findAllByOrganizationIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        currentUser.requireOrganizationId(), entityType, entityId)
                .stream()
                .map(AuditController::toResponse)
                .toList();
    }

    private static AuditEntryResponse toResponse(AuditLogEntry entry) {
        return new AuditEntryResponse(
                entry.getId(),
                entry.getActorUserId(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getMetadata(),
                entry.getCorrelationId(),
                entry.getOccurredAt());
    }
}
