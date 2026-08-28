package com.aishiftplanner.scheduler.schedule.application;

import com.aishiftplanner.scheduler.shared.config.AsyncConfig;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The one place a solver run actually leaves the request thread.
 *
 * <p>Its own bean because {@code @Async}, like {@code @Transactional}, is applied by a proxy:
 * a class calling its own {@code @Async} method executes it inline, which would turn a
 * 90-second solve into a 90-second HTTP request while looking asynchronous in the source.
 */
@Component
public class AsyncPlanningJobRunner {

    private final SchedulePlanningService schedulePlanningService;

    public AsyncPlanningJobRunner(SchedulePlanningService schedulePlanningService) {
        this.schedulePlanningService = schedulePlanningService;
    }

    @Async(AsyncConfig.PLANNING_EXECUTOR)
    public void run(UUID jobId) {
        schedulePlanningService.execute(jobId);
    }
}
