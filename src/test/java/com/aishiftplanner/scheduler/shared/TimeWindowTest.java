package com.aishiftplanner.scheduler.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeWindowTest {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 12);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);

    @Test
    void ordinaryShiftHasThePlainDuration() {
        TimeWindow kitchen = TimeWindow.of(SATURDAY, LocalTime.of(16, 0), LocalTime.of(23, 0), false);

        assertThat(kitchen.hours()).isCloseTo(7.0, within(0.001));
        assertThat(kitchen.startDate()).isEqualTo(SATURDAY);
    }

    @Test
    void midnightCrossingShiftLandsOnTheFollowingDay() {
        // The case a naive (date, start, end) triple gets wrong three different ways: the
        // duration would come out as -16 hours, the shift would appear to end before it
        // starts, and rest time to Sunday's shift would be measured from the wrong day.
        TimeWindow bar = TimeWindow.of(SATURDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), true);

        assertThat(bar.hours()).isCloseTo(8.0, within(0.001));
        assertThat(bar.end()).isEqualTo(LocalDateTime.of(SUNDAY, LocalTime.of(2, 0)));
        assertThat(bar.startDate()).isEqualTo(SATURDAY);
    }

    @Test
    void restIsMeasuredFromTheRealEndOfAMidnightCrossingShift() {
        // Bar closes at 02:00 Sunday; Sunday's 10:00 opening leaves 8 hours, not 32.
        TimeWindow saturdayBar = TimeWindow.of(SATURDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), true);
        TimeWindow sundayOpening = TimeWindow.of(SUNDAY, LocalTime.of(10, 0), LocalTime.of(16, 0), false);

        assertThat(saturdayBar.restBefore(sundayOpening).toHours()).isEqualTo(8);
    }

    @Test
    void overlappingShiftsAreDetectedAcrossMidnight() {
        TimeWindow saturdayBar = TimeWindow.of(SATURDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), true);
        TimeWindow sundayEarly = TimeWindow.of(SUNDAY, LocalTime.of(1, 0), LocalTime.of(6, 0), false);

        assertThat(saturdayBar.overlaps(sundayEarly)).isTrue();
        assertThat(sundayEarly.overlaps(saturdayBar)).isTrue();
    }

    @Test
    void backToBackShiftsDoNotOverlap() {
        TimeWindow morning = TimeWindow.of(SATURDAY, LocalTime.of(10, 0), LocalTime.of(16, 0), false);
        TimeWindow evening = TimeWindow.of(SATURDAY, LocalTime.of(16, 0), LocalTime.of(23, 0), false);

        assertThat(morning.overlaps(evening)).isFalse();
        assertThat(morning.restBefore(evening).toMinutes()).isZero();
    }

    @Test
    void aMidnightCrossingShiftTouchesBothCalendarDays() {
        TimeWindow bar = TimeWindow.of(SATURDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), true);

        assertThat(bar.touches(SATURDAY)).isTrue();
        assertThat(bar.touches(SUNDAY)).isTrue();
        assertThat(bar.touches(SATURDAY.minusDays(1))).isFalse();
    }

    @Test
    void eveningAndNightShiftsAreRecognised() {
        assertThat(TimeWindow.of(SATURDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), true).isEveningOrNight())
                .isTrue();
        assertThat(TimeWindow.of(SATURDAY, LocalTime.of(16, 0), LocalTime.of(23, 0), false).isEveningOrNight())
                .isTrue();
        assertThat(TimeWindow.of(SATURDAY, LocalTime.of(5, 0), LocalTime.of(12, 0), false).isEveningOrNight())
                .isTrue();
        assertThat(TimeWindow.of(SATURDAY, LocalTime.of(10, 0), LocalTime.of(16, 0), false).isEveningOrNight())
                .isFalse();
    }

    @Test
    void aWindowThatDoesNotAdvanceIsRejected() {
        assertThatThrownBy(() ->
                        TimeWindow.of(SATURDAY, LocalTime.of(16, 0), LocalTime.of(16, 0), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        TimeWindow.of(SATURDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
