package com.aishiftplanner.scheduler.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriodStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanningPeriodTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static PlanningPeriod periodWithDeadline(Instant deadline) {
        return new PlanningPeriod(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 13),
                deadline,
                UUID.randomUUID());
    }

    @Test
    void acceptsSubmissionsOneSecondBeforeTheDeadline() {
        Instant deadline = LocalDateTime.of(2026, 9, 2, 18, 0).atZone(BERLIN).toInstant();
        PlanningPeriod period = periodWithDeadline(deadline);

        assertThat(period.acceptsAvailabilityAt(deadline.minusSeconds(1))).isTrue();
    }

    @Test
    void refusesSubmissionsExactlyAtTheDeadline() {
        // The boundary matters: "18:00" in a rota is universally understood as "by 18:00",
        // so the instant itself must already be closed.
        Instant deadline = LocalDateTime.of(2026, 9, 2, 18, 0).atZone(BERLIN).toInstant();
        PlanningPeriod period = periodWithDeadline(deadline);

        assertThat(period.acceptsAvailabilityAt(deadline)).isFalse();
        assertThat(period.deadlineHasPassed(deadline)).isTrue();
    }

    @Test
    void refusesSubmissionsAfterTheDeadline() {
        Instant deadline = LocalDateTime.of(2026, 9, 2, 18, 0).atZone(BERLIN).toInstant();
        PlanningPeriod period = periodWithDeadline(deadline);

        assertThat(period.acceptsAvailabilityAt(deadline.plusSeconds(1))).isFalse();
    }

    @Test
    void refusesSubmissionsWhenTheStatusHasMovedOnEvenBeforeTheDeadline() {
        // Both gates must be open. A manager who has already started planning should not be
        // undercut by a late submission that arrives while the clock still allows it.
        Instant deadline = LocalDateTime.of(2026, 9, 2, 18, 0).atZone(BERLIN).toInstant();
        PlanningPeriod period = periodWithDeadline(deadline);
        period.setStatus(PlanningPeriodStatus.PLANNING);

        assertThat(period.acceptsAvailabilityAt(deadline.minusSeconds(3600))).isFalse();
    }

    @Test
    void reopeningRestoresBothTheStatusAndTheDeadline() {
        Instant deadline = LocalDateTime.of(2026, 9, 2, 18, 0).atZone(BERLIN).toInstant();
        PlanningPeriod period = periodWithDeadline(deadline);
        period.setStatus(PlanningPeriodStatus.READY_FOR_PLANNING);

        Instant newDeadline = deadline.plusSeconds(86_400);
        period.reopenAvailability(newDeadline);

        assertThat(period.getStatus()).isEqualTo(PlanningPeriodStatus.OPEN_FOR_AVAILABILITY);
        assertThat(period.acceptsAvailabilityAt(deadline.plusSeconds(60))).isTrue();
        assertThat(period.getAvailabilityDeadline()).isEqualTo(newDeadline);
    }

    @Test
    void deadlineSurvivesTheDaylightSavingTransition() {
        // Germany's clocks go back on 2026-10-25. A deadline of 02:30 local time that day is
        // ambiguous in wall-clock terms, which is exactly why the deadline is stored as an
        // Instant: whichever instant java.time resolves it to, the comparison stays total and
        // consistent rather than flip-flopping around the repeated hour.
        Instant deadline = LocalDateTime.of(2026, 10, 25, 2, 30).atZone(BERLIN).toInstant();
        PlanningPeriod period = periodWithDeadline(deadline);

        assertThat(period.acceptsAvailabilityAt(deadline.minusSeconds(1))).isTrue();
        assertThat(period.acceptsAvailabilityAt(deadline)).isFalse();
        assertThat(period.acceptsAvailabilityAt(deadline.plusSeconds(3600))).isFalse();
    }

    @Test
    void coversOnlyDatesInsideThePeriodInclusive() {
        PlanningPeriod period = periodWithDeadline(Instant.now());

        assertThat(period.covers(LocalDate.of(2026, 9, 6))).isFalse();
        assertThat(period.covers(LocalDate.of(2026, 9, 7))).isTrue();
        assertThat(period.covers(LocalDate.of(2026, 9, 10))).isTrue();
        assertThat(period.covers(LocalDate.of(2026, 9, 13))).isTrue();
        assertThat(period.covers(LocalDate.of(2026, 9, 14))).isFalse();
    }
}
