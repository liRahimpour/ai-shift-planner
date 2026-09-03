package com.aishiftplanner.scheduler.schedule.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.aishiftplanner.scheduler.shared.config.SchedulingProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates and configures the Timefold solver used for automatic shift planning.
 *
 * <p>The solver is configured programmatically instead of through XML so that important
 * runtime settings, such as the maximum solving time, can come directly from application
 * configuration.
 */
@Configuration
public class SolverFactoryProvider {

    /**
     * Creates the solver factory for shift schedules.
     *
     * @param properties application-specific scheduling configuration
     * @return configured Timefold solver factory
     */
    @Bean
    public SolverFactory<ShiftSchedule> shiftScheduleSolverFactory(
            SchedulingProperties properties) {

        long solverSeconds =
                properties.solverSecondsSpentLimit();

        Duration spentLimit =
                Duration.ofSeconds(solverSeconds);

        Duration unimprovedSpentLimit =
                Duration.ofSeconds(
                        Math.max(
                                5,
                                solverSeconds / 3));

        SolverConfig config =
                new SolverConfig()
                        .withSolutionClass(ShiftSchedule.class)
                        .withEntityClasses(ShiftSlot.class)
                        .withConstraintProviderClass(
                                ShiftScheduleConstraintProvider.class)
                        .withTerminationConfig(
                                new TerminationConfig()
                                        .withSpentLimit(spentLimit)
                                        .withUnimprovedSpentLimit(
                                                unimprovedSpentLimit));

        /*
         * Use a fixed random seed so that the solver's random decisions are repeatable
         * as far as possible.
         *
         * Timefold 2 removed EnvironmentMode.REPRODUCIBLE.
         * NO_ASSERT is its direct replacement and remains a reproducible environment mode.
         */
        config.setRandomSeed(1L);
        config.setEnvironmentMode(EnvironmentMode.NO_ASSERT);

        return SolverFactory.create(config);
    }
}