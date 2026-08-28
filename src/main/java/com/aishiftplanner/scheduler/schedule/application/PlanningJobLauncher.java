package com.aishiftplanner.scheduler.schedule.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers the handoff of a queued job until the transaction that created it has committed.
 *
 * <p>Without this, the worker thread races the caller's transaction: it can look for the job
 * row before that row is visible to other connections, find nothing, and quietly do nothing
 * at all — a planning request that vanishes with no error anywhere. Registering an
 * after-commit synchronization makes the handoff happen exactly once the job is real.
 */
@Component
public class PlanningJobLauncher {

    private final AsyncPlanningJobRunner runner;

    public PlanningJobLauncher(AsyncPlanningJobRunner runner) {
        this.runner = runner;
    }

    public void launchAfterCommit(UUID jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runner.run(jobId);
                }
            });
        } else {
            // No ambient transaction (a direct call from a test, or a future scheduled
            // trigger): nothing to wait for, so start immediately.
            runner.run(jobId);
        }
    }
}
