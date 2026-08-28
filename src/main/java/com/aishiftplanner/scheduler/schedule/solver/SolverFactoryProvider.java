package com.aishiftplanner.scheduler.schedule.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.aishiftplanner.scheduler.shared.config.SchedulingProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Timefold solver programmatically rather than through XML or starter
 * auto-configuration.
 *
 * <p>Two reasons. First, the time budget has to come from {@link SchedulingProperties} —
 * a small café's week and a twelve-location group's week deserve very different budgets, and
 * that has to be settable per deployment through an environment variable. Second, an explicit
 * configuration in Java is greppable: someone debugging why a plan looks odd can read the
 * termination and score rules here instead of hunting for an XML file whose presence they'd
 * have to know about.
 */
@Configuration
public class SolverFactoryProvider {

    @Bean
    public SolverFactory<ShiftSchedule> shiftScheduleSolverFactory(SchedulingProperties properties) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(ShiftSchedule.class)
                .withEntityClasses(ShiftSlot.class)
                .withConstraintProviderClass(ShiftScheduleConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofSeconds(properties.solverSecondsSpentLimit()))
                        // Stop early if nothing has improved for a third of the budget: past
                        // that point the search is almost always polishing noise, and giving
                        // the manager an answer sooner is worth more than the last 0.1%.
                        .withUnimprovedSpentLimit(
                                Duration.ofSeconds(
                                        Math.max(5, properties.solverSecondsSpentLimit() / 3))));

        // Deterministic runs: the same inputs must produce the same schedule, otherwise two
        // managers looking at "the fair plan" for the same week would see different rosters
        // and neither could explain why.
        config.setRandomSeed(1L);
        config.setEnvironmentMode(ai.timefold.solver.core.config.solver.EnvironmentMode.REPRODUCIBLE);

        return SolverFactory.create(config);
    }
}
