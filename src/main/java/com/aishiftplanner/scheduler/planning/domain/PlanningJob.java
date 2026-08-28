package com.aishiftplanner.scheduler.planning.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A request to generate schedules for a planning period.
 *
 * <p>Exists because a solver run takes tens of seconds and an HTTP request must not. It is
 * also the idempotency handle: a unique partial index on (period) where status is QUEUED or
 * RUNNING means a double-clicked "generate" button produces a conflict at the database, not
 * five concurrent solver runs competing to write the same schedules.
 */
@Entity
@Table(name = "planning_jobs")
public class PlanningJob extends TenantScopedEntity {

    public enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED;

        public boolean isActive() {
            return this == QUEUED || this == RUNNING;
        }

        public boolean isTerminal() {
            return !isActive();
        }
    }

    @Column(name = "planning_period_id", nullable = false)
    private UUID planningPeriodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.QUEUED;

    @Column(name = "requested_by")
    private UUID requestedBy;

    /** Set on failure; a human-readable reason, never a stack trace. */
    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    /** Comma-separated strategy names this run should produce. */
    @Column(name = "strategies", nullable = false, length = 200)
    private String strategies = "FAIR,COST_OPTIMIZED,BALANCED";

    @Column(name = "progress_note", length = 200)
    private String progressNote;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected PlanningJob() {
        // for JPA
    }

    public PlanningJob(UUID organizationId, UUID planningPeriodId, UUID requestedBy) {
        setOrganizationId(organizationId);
        this.planningPeriodId = planningPeriodId;
        this.requestedBy = requestedBy;
    }

    public void markRunning(Instant now) {
        this.status = Status.RUNNING;
        this.startedAt = now;
    }

    public void markCompleted(Instant now) {
        this.status = Status.COMPLETED;
        this.finishedAt = now;
        this.progressNote = null;
    }

    public void markFailed(Instant now, String reason) {
        this.status = Status.FAILED;
        this.finishedAt = now;
        this.failureReason = reason;
    }

    public void markCancelled(Instant now) {
        this.status = Status.CANCELLED;
        this.finishedAt = now;
    }

    public UUID getPlanningPeriodId() {
        return planningPeriodId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getStrategies() {
        return strategies;
    }

    public void setStrategies(String strategies) {
        this.strategies = strategies;
    }

    public String getProgressNote() {
        return progressNote;
    }

    public void setProgressNote(String progressNote) {
        this.progressNote = progressNote;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
