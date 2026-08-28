package com.aishiftplanner.scheduler.schedule.application;

import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.planning.application.PlanningPeriodService;
import com.aishiftplanner.scheduler.planning.domain.PlanningJob;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningJobRepository;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.PlanningJobResponse;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts planning requests, enforces idempotency, and hands work to the async executor.
 */
@Service
public class PlanningJobService {

    private static final Logger log = LoggerFactory.getLogger(PlanningJobService.class);

    private static final List<PlanningJob.Status> ACTIVE_STATUSES =
            List.of(PlanningJob.Status.QUEUED, PlanningJob.Status.RUNNING);

    private final PlanningJobRepository planningJobRepository;
    private final PlanningPeriodService planningPeriodService;
    private final PlanningJobLauncher jobLauncher;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public PlanningJobService(
            PlanningJobRepository planningJobRepository,
            PlanningPeriodService planningPeriodService,
            PlanningJobLauncher jobLauncher,
            CurrentUserProvider currentUser,
            Clock clock) {
        this.planningJobRepository = planningJobRepository;
        this.planningPeriodService = planningPeriodService;
        this.jobLauncher = jobLauncher;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Queues a planning run, or returns the run already in flight.
     *
     * <p>Idempotency is enforced twice on purpose. The application-level check gives a fast,
     * friendly answer ("this is already running, here is its id"), while a unique partial
     * index in the database is what actually makes it correct: two requests arriving
     * simultaneously can both pass the application check before either has written its row,
     * and only the database can arbitrate that race. Returning the existing job rather than an
     * error means a double-clicked button just does the right thing.
     */
    @Transactional
    public PlanningJobResponse requestPlanning(UUID planningPeriodId) {
        PlanningPeriod period = planningPeriodService.loadInTenant(planningPeriodId);

        if (!period.getStatus().allowsPlanning() && !period.deadlineHasPassed(clock.instant())) {
            throw new ApiException(
                    ErrorCode.PLANNING_PERIOD_NOT_READY,
                    org.springframework.http.HttpStatus.CONFLICT,
                    "This planning period is not ready for planning yet. Close the availability "
                            + "deadline first, or reopen and then close it.");
        }

        var existing = planningJobRepository
                .findFirstByPlanningPeriodIdAndStatusInOrderByCreatedAtDesc(planningPeriodId, ACTIVE_STATUSES);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        PlanningJob job = new PlanningJob(
                period.getOrganizationId(), planningPeriodId, currentUser.require().userId());

        PlanningJob saved;
        try {
            saved = planningJobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException ex) {
            // The database won the race. Return whatever job actually got created, so the
            // second click sees the first click's job rather than an error.
            log.debug("Concurrent planning request for period {}; returning the winning job", planningPeriodId);
            return planningJobRepository
                    .findFirstByPlanningPeriodIdAndStatusInOrderByCreatedAtDesc(
                            planningPeriodId, ACTIVE_STATUSES)
                    .map(PlanningJobService::toResponse)
                    .orElseThrow(() -> ApiException.conflict(
                            ErrorCode.CONFLICT, "A planning run for this period is already in progress."));
        }

        jobLauncher.launchAfterCommit(saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PlanningJobResponse get(UUID jobId) {
        return planningJobRepository
                .findByIdAndOrganizationId(jobId, currentUser.requireOrganizationId())
                .map(PlanningJobService::toResponse)
                .orElseThrow(() -> ApiException.notFound("Planning job not found."));
    }

    @Transactional(readOnly = true)
    public List<PlanningJobResponse> listForPeriod(UUID planningPeriodId) {
        planningPeriodService.loadInTenant(planningPeriodId);
        return planningJobRepository.findAllByPlanningPeriodIdOrderByCreatedAtDesc(planningPeriodId).stream()
                .map(PlanningJobService::toResponse)
                .toList();
    }

    @Transactional
    public PlanningJobResponse cancel(UUID jobId) {
        PlanningJob job = planningJobRepository
                .findByIdAndOrganizationId(jobId, currentUser.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Planning job not found."));

        if (job.getStatus().isTerminal()) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "This planning job has already finished.");
        }
        // Marks the job cancelled so it stops blocking new runs. A solver already mid-run
        // finishes its current strategy rather than being interrupted; a half-written set of
        // proposals would be worse than one extra completed option.
        job.markCancelled(clock.instant());
        return toResponse(planningJobRepository.save(job));
    }

    static PlanningJobResponse toResponse(PlanningJob job) {
        return new PlanningJobResponse(
                job.getId(),
                job.getPlanningPeriodId(),
                job.getStatus(),
                job.getProgressNote(),
                job.getFailureReason(),
                job.getStartedAt(),
                job.getFinishedAt());
    }
}
