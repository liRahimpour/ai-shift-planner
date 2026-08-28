package com.aishiftplanner.scheduler.schedule.application;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import com.aishiftplanner.scheduler.planning.domain.PlanningJob;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningJobRepository;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningPeriodRepository;
import com.aishiftplanner.scheduler.schedule.domain.PlanningStrategy;
import com.aishiftplanner.scheduler.schedule.domain.ShiftAssignment;
import com.aishiftplanner.scheduler.schedule.infrastructure.ScheduleRepository;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftAssignmentRepository;
import com.aishiftplanner.scheduler.schedule.solver.ConstraintWeights;
import com.aishiftplanner.scheduler.schedule.solver.ShiftSchedule;
import com.aishiftplanner.scheduler.shared.config.SchedulingProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the solver for each requested strategy and hands the results to the writers.
 *
 * <p>Invoked from the async executor, never directly from a controller.
 *
 * <p>The important structural decision here: <b>solving happens outside any transaction</b>.
 * A solver run lasting tens of seconds inside an open transaction would hold a database
 * connection and its locks for the whole run; with three strategies and a few concurrent
 * managers that exhausts the connection pool and takes the whole application down. So the
 * problem is loaded in one short read-only transaction, solved with no database involvement
 * at all, and written back in another short transaction.
 */
@Service
public class SchedulePlanningService {

    private static final Logger log = LoggerFactory.getLogger(SchedulePlanningService.class);

    private final SolverFactory<ShiftSchedule> solverFactory;
    private final PlanningProblemLoader problemLoader;
    private final ScheduleProposalWriter proposalWriter;
    private final PlanningJobStateWriter jobStateWriter;
    private final ScheduleRepository scheduleRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final PlanningPeriodRepository planningPeriodRepository;
    private final PlanningJobRepository planningJobRepository;
    private final SchedulingProperties schedulingProperties;

    public SchedulePlanningService(
            SolverFactory<ShiftSchedule> solverFactory,
            PlanningProblemLoader problemLoader,
            ScheduleProposalWriter proposalWriter,
            PlanningJobStateWriter jobStateWriter,
            ScheduleRepository scheduleRepository,
            ShiftAssignmentRepository assignmentRepository,
            PlanningPeriodRepository planningPeriodRepository,
            PlanningJobRepository planningJobRepository,
            SchedulingProperties schedulingProperties) {
        this.solverFactory = solverFactory;
        this.problemLoader = problemLoader;
        this.proposalWriter = proposalWriter;
        this.jobStateWriter = jobStateWriter;
        this.scheduleRepository = scheduleRepository;
        this.assignmentRepository = assignmentRepository;
        this.planningPeriodRepository = planningPeriodRepository;
        this.planningJobRepository = planningJobRepository;
        this.schedulingProperties = schedulingProperties;
    }

    /** Executes one planning job end to end. Never throws; failures land on the job row. */
    public void execute(UUID jobId) {
        PlanningJob job = loadJob(jobId);
        if (job == null || job.getStatus() != PlanningJob.Status.QUEUED) {
            log.warn("Planning job {} is not queued; skipping", jobId);
            return;
        }

        PlanningPeriod period = loadPeriod(job.getPlanningPeriodId());
        if (period == null) {
            jobStateWriter.markFailed(jobId, "The planning period no longer exists.");
            return;
        }

        jobStateWriter.markRunning(jobId);
        try {
            List<PlanningStrategy> strategies = parseStrategies(job.getStrategies());
            List<ShiftAssignment> pinned = collectPinnedAssignments(period.getId());

            for (PlanningStrategy strategy : strategies) {
                jobStateWriter.noteProgress(jobId, "Solving " + strategy.name());

                ShiftSchedule problem = problemLoader.load(
                        period, ConstraintWeights.forStrategy(strategy, schedulingProperties), pinned);

                if (problem.getSlots().isEmpty()) {
                    jobStateWriter.markFailed(
                            jobId, "No shifts exist for this planning period. Generate shifts first.");
                    return;
                }
                if (problem.getEmployees().isEmpty()) {
                    jobStateWriter.markFailed(
                            jobId,
                            "No active employees are cleared for this location, so nothing can be planned.");
                    return;
                }

                Solver<ShiftSchedule> solver = solverFactory.buildSolver();
                ShiftSchedule solved = solver.solve(problem);

                proposalWriter.persist(period, strategy, solved);
            }

            jobStateWriter.markCompleted(jobId, strategies.size());
        } catch (RuntimeException ex) {
            log.error("Planning job {} failed", jobId, ex);
            // The manager sees a sentence, not a stack trace; the trace goes to the log,
            // correlated by the job id they can quote.
            jobStateWriter.markFailed(jobId, "Planning failed unexpectedly. Reference: " + jobId);
        }
    }

    // These helpers carry no @Transactional annotation deliberately. They are called from
    // execute() on the same bean, and Spring's transaction proxy is bypassed on
    // self-invocation - an annotation here would look like it did something and do nothing.
    // Each repository call is transactional in its own right, which is all these reads need.

    private PlanningJob loadJob(UUID jobId) {
        return planningJobRepository.findById(jobId).orElse(null);
    }

    private PlanningPeriod loadPeriod(UUID periodId) {
        return planningPeriodRepository.findById(periodId).orElse(null);
    }

    /**
     * Collects pinned assignments from whichever proposal is currently selected.
     *
     * <p>Pins live on a schedule but express a decision about the <em>period</em> — "Anna
     * works Saturday evening, whatever else changes". Carrying them across a regeneration is
     * the behaviour a manager expects after fixing one assignment by hand and asking for
     * fresh options; losing them is what makes people stop trusting the button.
     */
    private List<ShiftAssignment> collectPinnedAssignments(UUID planningPeriodId) {
        return scheduleRepository
                .findByPlanningPeriodIdAndSelectedTrue(planningPeriodId)
                .map(schedule -> assignmentRepository.findAllByScheduleIdAndPinnedTrue(schedule.getId()))
                .orElseGet(List::of);
    }

    private List<PlanningStrategy> parseStrategies(String csv) {
        List<PlanningStrategy> strategies = new ArrayList<>();
        for (String name : csv.split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                strategies.add(PlanningStrategy.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) {
                log.warn("Ignoring unknown planning strategy '{}'", trimmed);
            }
        }
        return strategies.isEmpty()
                ? List.of(PlanningStrategy.FAIR, PlanningStrategy.COST_OPTIMIZED, PlanningStrategy.BALANCED)
                : strategies;
    }
}
