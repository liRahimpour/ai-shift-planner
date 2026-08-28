package com.aishiftplanner.scheduler.schedule.application;

import com.aishiftplanner.scheduler.audit.application.AuditService;
import com.aishiftplanner.scheduler.audit.domain.AuditAction;
import com.aishiftplanner.scheduler.planning.domain.PlanningJob;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriodStatus;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningJobRepository;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningPeriodRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job and period state transitions, each in its own short transaction.
 *
 * <p>This is a separate bean rather than a set of methods on {@code SchedulePlanningService}
 * for a specific reason: Spring's {@code @Transactional} works through a proxy, so a method
 * calling its own {@code @Transactional} sibling bypasses it entirely. State updates written
 * that way would silently join the caller's transaction (or none at all) — meaning a job
 * marked RUNNING would not actually be visible to anyone polling it, and a failure would roll
 * back the very record of the failure.
 */
@Service
public class PlanningJobStateWriter {

    private final PlanningJobRepository planningJobRepository;
    private final PlanningPeriodRepository planningPeriodRepository;
    private final AuditService auditService;
    private final Clock clock;

    public PlanningJobStateWriter(
            PlanningJobRepository planningJobRepository,
            PlanningPeriodRepository planningPeriodRepository,
            AuditService auditService,
            Clock clock) {
        this.planningJobRepository = planningJobRepository;
        this.planningPeriodRepository = planningPeriodRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(UUID jobId) {
        planningJobRepository.findById(jobId).ifPresent(job -> {
            job.markRunning(clock.instant());
            planningJobRepository.save(job);
            planningPeriodRepository.findById(job.getPlanningPeriodId()).ifPresent(period -> {
                period.setStatus(PlanningPeriodStatus.PLANNING);
                planningPeriodRepository.save(period);
            });
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void noteProgress(UUID jobId, String note) {
        planningJobRepository.findById(jobId).ifPresent(job -> {
            job.setProgressNote(note);
            planningJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID jobId, int proposalCount) {
        planningJobRepository.findById(jobId).ifPresent(job -> {
            job.markCompleted(clock.instant());
            planningJobRepository.save(job);
            planningPeriodRepository.findById(job.getPlanningPeriodId()).ifPresent(period -> {
                period.setStatus(PlanningPeriodStatus.DRAFT);
                planningPeriodRepository.save(period);
            });
            auditService.recordAs(
                    job.getOrganizationId(),
                    job.getRequestedBy(),
                    AuditAction.SCHEDULE_GENERATED,
                    "PlanningPeriod",
                    job.getPlanningPeriodId(),
                    Map.of("jobId", jobId.toString(), "proposals", String.valueOf(proposalCount)));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, String reason) {
        planningJobRepository.findById(jobId).ifPresent(job -> {
            job.markFailed(clock.instant(), reason);
            planningJobRepository.save(job);
            // Return the period to a state the manager can act from, instead of leaving it
            // stuck in PLANNING with no running job behind it.
            planningPeriodRepository.findById(job.getPlanningPeriodId()).ifPresent(period -> {
                if (period.getStatus() == PlanningPeriodStatus.PLANNING) {
                    period.setStatus(PlanningPeriodStatus.READY_FOR_PLANNING);
                    planningPeriodRepository.save(period);
                }
            });
        });
    }

    @Transactional(readOnly = true)
    public PlanningJob.Status statusOf(UUID jobId) {
        return planningJobRepository
                .findById(jobId)
                .map(PlanningJob::getStatus)
                .orElse(null);
    }
}
