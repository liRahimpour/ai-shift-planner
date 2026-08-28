package com.aishiftplanner.scheduler.availability;

import static org.assertj.core.api.Assertions.assertThat;

import com.aishiftplanner.scheduler.availability.domain.Availability;
import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvailabilityTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    private static Availability window(AvailabilityType type, LocalTime start, LocalTime end) {
        return new Availability(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), MONDAY, type, start, end);
    }

    @Test
    void wholeDayWindowCoversEverything() {
        Availability wholeDay = window(AvailabilityType.AVAILABLE, null, null);

        assertThat(wholeDay.isWholeDay()).isTrue();
        assertThat(wholeDay.covers(LocalTime.of(6, 0), LocalTime.of(23, 0))).isTrue();
    }

    @Test
    void coverageRequiresFullContainmentNotMereOverlap() {
        // This is the constraint that decides whether someone available 08:00-16:00 can be
        // put on a 14:00-22:00 shift. They cannot - the shift runs six hours past what they
        // offered. Testing overlap here instead of containment would produce schedules that
        // look valid and are not.
        Availability morning = window(AvailabilityType.AVAILABLE, LocalTime.of(8, 0), LocalTime.of(16, 0));

        assertThat(morning.covers(LocalTime.of(9, 0), LocalTime.of(15, 0))).isTrue();
        assertThat(morning.covers(LocalTime.of(8, 0), LocalTime.of(16, 0))).isTrue();
        assertThat(morning.covers(LocalTime.of(14, 0), LocalTime.of(22, 0))).isFalse();
        assertThat(morning.covers(LocalTime.of(6, 0), LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void overlapIsLooserThanCoverage() {
        Availability morning = window(AvailabilityType.AVAILABLE, LocalTime.of(8, 0), LocalTime.of(16, 0));

        assertThat(morning.overlaps(LocalTime.of(14, 0), LocalTime.of(22, 0))).isTrue();
        assertThat(morning.overlaps(LocalTime.of(16, 0), LocalTime.of(22, 0))).isFalse();
        assertThat(morning.overlaps(LocalTime.of(5, 0), LocalTime.of(8, 0))).isFalse();
    }

    @Test
    void adjacentWindowsDoNotOverlap() {
        // 10:00-14:00 and 14:00-18:00 are a legitimate split day, not a contradiction.
        Availability first = window(AvailabilityType.AVAILABLE, LocalTime.of(10, 0), LocalTime.of(14, 0));

        assertThat(first.overlaps(LocalTime.of(14, 0), LocalTime.of(18, 0))).isFalse();
    }

    @Test
    void unavailableIsAHardBlockAndPreferredIsAWish() {
        assertThat(AvailabilityType.UNAVAILABLE.isHardBlock()).isTrue();
        assertThat(AvailabilityType.PREFERRED.isHardBlock()).isFalse();
        assertThat(AvailabilityType.PREFERRED.isWish()).isTrue();
        assertThat(AvailabilityType.AVAILABLE.isWish()).isFalse();
        assertThat(AvailabilityType.AVAILABLE.isHardBlock()).isFalse();
    }
}
