package com.aishiftplanner.scheduler.shared.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool for solver runs.
 *
 * <p>Sized deliberately small and with a bounded queue. A solver run is CPU-bound and will
 * happily use a whole core for its entire time budget; running many at once does not make any
 * of them finish sooner, it just makes all of them slower and starves the web threads that
 * serve the rest of the application.
 *
 * <p>{@link ThreadPoolExecutor.CallerRunsPolicy} on saturation is a deliberate choice over
 * silently discarding work: if the queue is full, the submitting thread runs the job itself,
 * which slows the caller down and creates natural back-pressure instead of losing a manager's
 * planning request without a trace.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String PLANNING_EXECUTOR = "planningExecutor";

    @Bean(name = PLANNING_EXECUTOR)
    public Executor planningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("planning-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Let in-flight solver runs finish during a Kubernetes rolling update rather than
        // being killed mid-solve, which would leave a job stuck in RUNNING forever.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
