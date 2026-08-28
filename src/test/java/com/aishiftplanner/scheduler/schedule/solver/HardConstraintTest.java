package com.aishiftplanner.scheduler.schedule.solver;

import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.BAR_DEPARTMENT;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.BAR_SKILL;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.CLOSING_SKILL;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.KITCHEN_DEPARTMENT;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.LOCATION;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.MONDAY;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.OTHER_LOCATION;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.SATURDAY;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.SUNDAY;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.emptySlot;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.employee;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.shift;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.shiftAtLocation;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.shiftInDepartment;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.shiftRequiringSkills;
import static com.aishiftplanner.scheduler.schedule.solver.SolverTestData.slot;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Hard constraints: the rules that a publishable schedule may never break.
 *
 * <p>Each test states a business situation and asserts the exact number of penalties. Exact
 * counts rather than "at least one" matter here: a constraint that fires twice for one
 * violation silently outweighs the others and skews every plan the solver produces.
 */
class HardConstraintTest {

    private final ConstraintVerifier<ShiftScheduleConstraintProvider, ShiftSchedule> verifier =
            ConstraintVerifier.build(
                    new ShiftScheduleConstraintProvider(), ShiftSchedule.class, ShiftSlot.class);

    private static final ConstraintWeights WEIGHTS = ConstraintWeights.balanced();

    // --- Availability --------------------------------------------------------

    @Test
    void schedulingSomeoneInsideTheirDeclaredWindowIsFine() {
        var anna = employee("Anna")
                .available(SATURDAY, AvailabilityType.AVAILABLE, "10:00", "20:00")
                .build();
        var kitchen = shift(SATURDAY, "12:00", "18:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustBeAvailable)
                .given(slot(kitchen, 0, anna))
                .penalizesBy(0);
    }

    @Test
    void schedulingSomeoneOutsideTheirWindowIsAHardViolation() {
        // Available 10:00-16:00, asked to work 14:00-22:00: six hours they never offered.
        var anna = employee("Anna")
                .available(SATURDAY, AvailabilityType.AVAILABLE, "10:00", "16:00")
                .build();
        var evening = shift(SATURDAY, "14:00", "22:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustBeAvailable)
                .given(slot(evening, 0, anna))
                .penalizesBy(1);
    }

    @Test
    void aDayWithNoDeclarationCountsAsUnavailable() {
        // Silence is not consent. Someone who never submitted anything for Sunday must not be
        // rostered on it just because nothing says they cannot be.
        var anna = employee("Anna")
                .available(SATURDAY, AvailabilityType.AVAILABLE, "10:00", "20:00")
                .build();
        var sundayShift = shift(SUNDAY, "12:00", "18:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustBeAvailable)
                .given(slot(sundayShift, 0, anna))
                .penalizesBy(1);
    }

    @Test
    void anExplicitUnavailableWindowOverridesAnOverlappingAvailableOne() {
        var anna = employee("Anna")
                .availableAllDay(SATURDAY, AvailabilityType.AVAILABLE)
                .available(SATURDAY, AvailabilityType.UNAVAILABLE, "14:00", "18:00")
                .build();
        var afternoon = shift(SATURDAY, "15:00", "17:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustBeAvailable)
                .given(slot(afternoon, 0, anna))
                .penalizesBy(1);
    }

    @Test
    void availabilityRunningToEndOfDayCoversAnOvernightShift() {
        // The bar closes at 02:00, but availability is declared per calendar day. Someone
        // available 17:00-23:59 on Saturday must be eligible for the Saturday 18:00-02:00
        // shift. Comparing against LocalTime.MAX instead would fail this by a fraction of a
        // second and quietly make every bar shift unstaffable.
        var sarah = employee("Sarah")
                .available(SATURDAY, AvailabilityType.AVAILABLE, "17:00", "23:59")
                .build();
        var bar = shift(SATURDAY, "18:00", "02:00", true, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustBeAvailable)
                .given(slot(bar, 0, sarah))
                .penalizesBy(0);
    }

    @Test
    void availabilityEndingEarlyDoesNotCoverAnOvernightShift() {
        var sarah = employee("Sarah")
                .available(SATURDAY, AvailabilityType.AVAILABLE, "17:00", "21:00")
                .build();
        var bar = shift(SATURDAY, "18:00", "02:00", true, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustBeAvailable)
                .given(slot(bar, 0, sarah))
                .penalizesBy(1);
    }

    // --- Overlaps ------------------------------------------------------------

    @Test
    void thesamePersonCannotWorkTwoOverlappingShifts() {
        var anna = employee("Anna").availableAllDay(SATURDAY, AvailabilityType.AVAILABLE).build();
        var bar = shift(SATURDAY, "18:00", "23:00", false, 1, 1);
        var kitchen = shift(SATURDAY, "20:00", "23:30", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::noOverlappingShifts)
                .given(slot(bar, 0, anna), slot(kitchen, 0, anna))
                .penalizesBy(1);
    }

    @Test
    void backToBackShiftsDoNotCountAsOverlapping() {
        var anna = employee("Anna").availableAllDay(SATURDAY, AvailabilityType.AVAILABLE).build();
        var morning = shift(SATURDAY, "10:00", "16:00", false, 1, 1);
        var evening = shift(SATURDAY, "16:00", "22:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::noOverlappingShifts)
                .given(slot(morning, 0, anna), slot(evening, 0, anna))
                .penalizesBy(0);
    }

    @Test
    void aMidnightCrossingShiftOverlapsTheNextMorning() {
        // The case a naive same-day comparison misses entirely: the bar closes at 02:00 on
        // Sunday, so a Sunday 01:00 start is a genuine double-booking.
        var anna = employee("Anna").availableAllDay(SATURDAY, AvailabilityType.AVAILABLE).build();
        var saturdayBar = shift(SATURDAY, "18:00", "02:00", true, 1, 1);
        var sundayEarly = shift(SUNDAY, "01:00", "06:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::noOverlappingShifts)
                .given(slot(saturdayBar, 0, anna), slot(sundayEarly, 0, anna))
                .penalizesBy(1);
    }

    @Test
    void twoPeopleOnOverlappingShiftsAreFine() {
        var anna = employee("Anna").availableAllDay(SATURDAY, AvailabilityType.AVAILABLE).build();
        var ben = employee("Ben").availableAllDay(SATURDAY, AvailabilityType.AVAILABLE).build();
        var bar = shift(SATURDAY, "18:00", "23:00", false, 2, 2);

        verifier.verifyThat(ShiftScheduleConstraintProvider::noOverlappingShifts)
                .given(slot(bar, 0, anna), slot(bar, 1, ben))
                .penalizesBy(0);
    }

    // --- Location and department ---------------------------------------------

    @Test
    void anEmployeeCannotBeScheduledAtALocationTheyAreNotClearedFor() {
        var anna = employee("Anna").at(LOCATION).build();
        var elsewhere = shiftAtLocation(OTHER_LOCATION, SATURDAY, "10:00", "16:00");

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustBeClearedForLocation)
                .given(slot(elsewhere, 0, anna))
                .penalizesBy(1);
    }

    @Test
    void anEmployeeClearedForASecondLocationMayWorkThere() {
        var anna = employee("Anna").at(LOCATION).alsoClearedFor(OTHER_LOCATION).build();
        var elsewhere = shiftAtLocation(OTHER_LOCATION, SATURDAY, "10:00", "16:00");

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustBeClearedForLocation)
                .given(slot(elsewhere, 0, anna))
                .penalizesBy(0);
    }

    @Test
    void anEmployeeCannotBeScheduledInADepartmentTheyDoNotWorkIn() {
        var barista = employee("Ben").inDepartments(BAR_DEPARTMENT).build();
        var kitchenShift = shiftInDepartment(KITCHEN_DEPARTMENT, SATURDAY, "10:00", "16:00");

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustWorkInDepartment)
                .given(slot(kitchenShift, 0, barista))
                .penalizesBy(1);
    }

    @Test
    void anEmployeeWithNoRecordedDepartmentsIsTreatedAsDeployableAnywhere() {
        // Incomplete master data should degrade to a slightly loose assignment a manager can
        // correct, not to an unsolvable week.
        var newcomer = employee("Chris").build();
        var kitchenShift = shiftInDepartment(KITCHEN_DEPARTMENT, SATURDAY, "10:00", "16:00");

        verifier.verifyThat(ShiftScheduleConstraintProvider::employeeMustWorkInDepartment)
                .given(slot(kitchenShift, 0, newcomer))
                .penalizesBy(0);
    }

    // --- Skills --------------------------------------------------------------

    @Test
    void aShiftMissingItsRequiredSkillIsPenalizedOncePerMissingHolder() {
        var withoutBar = employee("Anna").build();
        var barShift = shiftRequiringSkills(SATURDAY, "18:00", "23:00", 2, Map.of(BAR_SKILL, 1));

        verifier.verifyThat(ShiftScheduleConstraintProvider::requiredSkillsMustBeCovered)
                .given(slot(barShift, 0, withoutBar), slot(barShift, 1, withoutBar))
                .penalizesBy(1);
    }

    @Test
    void aShiftWithItsRequiredSkillCoveredIsFine() {
        var bartender = employee("Sarah").withSkills(BAR_SKILL).build();
        var other = employee("Anna").build();
        var barShift = shiftRequiringSkills(SATURDAY, "18:00", "23:00", 2, Map.of(BAR_SKILL, 1));

        verifier.verifyThat(ShiftScheduleConstraintProvider::requiredSkillsMustBeCovered)
                .given(slot(barShift, 0, bartender), slot(barShift, 1, other))
                .penalizesBy(0);
    }

    @Test
    void onePersonHoldingBothSkillsSatisfiesBothDemands() {
        // A requirement of "1x BAR, 1x CLOSING" is about the skills being present on the
        // floor, not about two distinct people holding them.
        var allRounder = employee("Sarah").withSkills(BAR_SKILL, CLOSING_SKILL).build();
        var barShift = shiftRequiringSkills(
                SATURDAY, "18:00", "23:00", 1, Map.of(BAR_SKILL, 1, CLOSING_SKILL, 1));

        verifier.verifyThat(ShiftScheduleConstraintProvider::requiredSkillsMustBeCovered)
                .given(slot(barShift, 0, allRounder))
                .penalizesBy(0);
    }

    @Test
    void demandingTwoHoldersWhenOnlyOneIsAssignedIsPenalizedByTheShortfall() {
        var bartender = employee("Sarah").withSkills(BAR_SKILL).build();
        var other = employee("Anna").build();
        var barShift = shiftRequiringSkills(SATURDAY, "18:00", "23:00", 2, Map.of(BAR_SKILL, 2));

        verifier.verifyThat(ShiftScheduleConstraintProvider::requiredSkillsMustBeCovered)
                .given(slot(barShift, 0, bartender), slot(barShift, 1, other))
                .penalizesBy(1);
    }

    // --- Minimum staffing ----------------------------------------------------

    @Test
    void aShiftBelowItsMinimumStaffingIsPenalizedBySeatsShort() {
        var anna = employee("Anna").build();
        var kitchen = shift(SATURDAY, "16:00", "23:00", false, 4, 3);

        verifier.verifyThat(ShiftScheduleConstraintProvider::minimumStaffingMustBeMet)
                .given(
                        slot(kitchen, 0, anna),
                        emptySlot(kitchen, 1),
                        emptySlot(kitchen, 2),
                        emptySlot(kitchen, 3))
                .penalizesBy(2);
    }

    @Test
    void aCompletelyUnstaffedShiftStillReportsItsFullShortfall() {
        // The silent-failure case: with a stream that drops unassigned seats, a shift nobody
        // was assigned to would score as if it were fine.
        var kitchen = shift(SATURDAY, "16:00", "23:00", false, 3, 3);

        verifier.verifyThat(ShiftScheduleConstraintProvider::minimumStaffingMustBeMet)
                .given(emptySlot(kitchen, 0), emptySlot(kitchen, 1), emptySlot(kitchen, 2))
                .penalizesBy(3);
    }

    @Test
    void meetingTheMinimumIsFineEvenWhenTheTargetIsNotReached() {
        var anna = employee("Anna").build();
        var ben = employee("Ben").build();
        var kitchen = shift(SATURDAY, "16:00", "23:00", false, 4, 2);

        verifier.verifyThat(ShiftScheduleConstraintProvider::minimumStaffingMustBeMet)
                .given(slot(kitchen, 0, anna), slot(kitchen, 1, ben), emptySlot(kitchen, 2))
                .penalizesBy(0);
    }

    // --- Working time and rest -----------------------------------------------

    @Test
    void exceedingTheWeeklyMaximumIsPenalizedByTheExcessMinutes() {
        var anna = employee("Anna").withMaximumHours(10).build();
        var saturday = shift(SATURDAY, "10:00", "18:00", false, 1, 1); // 8h
        var sunday = shift(SUNDAY, "10:00", "14:00", false, 1, 1); // 4h → 12h total, 2h over

        verifier.verifyThat(ShiftScheduleConstraintProvider::maximumWeeklyHoursMustNotBeExceeded)
                .given(slot(saturday, 0, anna), slot(sunday, 0, anna))
                .penalizesBy(120);
    }

    @Test
    void stayingWithinTheWeeklyMaximumIsFine() {
        var anna = employee("Anna").withMaximumHours(40).build();
        var saturday = shift(SATURDAY, "10:00", "18:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::maximumWeeklyHoursMustNotBeExceeded)
                .given(slot(saturday, 0, anna))
                .penalizesBy(0);
    }

    @Test
    void tooLittleRestBetweenTwoShiftsIsPenalizedByTheMissingMinutes() {
        // Closing at 23:00 and opening at 08:00 leaves nine hours; the configured minimum is
        // eleven, so two hours are missing. This is the "Anna would only have 9 hours' rest"
        // warning the manager sees after a manual edit.
        var anna = employee("Anna").build();
        var saturdayEvening = shift(SATURDAY, "16:00", "23:00", false, 1, 1);
        var sundayMorning = shift(SUNDAY, "08:00", "14:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::minimumRestBetweenShiftsMustBeRespected)
                .given(WEIGHTS, slot(saturdayEvening, 0, anna), slot(sundayMorning, 0, anna))
                .penalizesBy(120);
    }

    @Test
    void enoughRestBetweenTwoShiftsIsFine() {
        var anna = employee("Anna").build();
        var saturdayEvening = shift(SATURDAY, "16:00", "22:00", false, 1, 1);
        var mondayMorning = shift(MONDAY, "09:00", "15:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::minimumRestBetweenShiftsMustBeRespected)
                .given(WEIGHTS, slot(saturdayEvening, 0, anna), slot(mondayMorning, 0, anna))
                .penalizesBy(0);
    }

    @Test
    void restIsMeasuredFromTheRealEndOfAMidnightCrossingShift() {
        // Bar closes 02:00 Sunday, Sunday opening at 10:00 → 8 hours, 3 short of the minimum.
        // Measured naively from Saturday's "02:00", this would look like 32 hours of rest.
        var anna = employee("Anna").build();
        var saturdayBar = shift(SATURDAY, "18:00", "02:00", true, 1, 1);
        var sundayOpening = shift(SUNDAY, "10:00", "16:00", false, 1, 1);

        verifier.verifyThat(ShiftScheduleConstraintProvider::minimumRestBetweenShiftsMustBeRespected)
                .given(WEIGHTS, slot(saturdayBar, 0, anna), slot(sundayOpening, 0, anna))
                .penalizesBy(180);
    }

    @Test
    void exceedingTheDailyMaximumIsPenalizedByTheExcessMinutes() {
        var anna = employee("Anna").build();
        var morning = shift(SATURDAY, "08:00", "14:00", false, 1, 1); // 6h
        var evening = shift(SATURDAY, "15:00", "21:00", false, 1, 1); // 6h → 12h, 2h over 10h

        verifier.verifyThat(ShiftScheduleConstraintProvider::maximumDailyHoursMustNotBeExceeded)
                .given(WEIGHTS, slot(morning, 0, anna), slot(evening, 0, anna))
                .penalizesBy(120);
    }

    @Test
    void aMidnightCrossingShiftCountsAgainstTheDayItStarted() {
        // 18:00-02:00 is eight hours on Saturday, not six on Saturday and two on Sunday.
        // Grouping it by the day it ends would hide daily-limit breaches around closing time.
        var anna = employee("Anna").build();
        var bar = shift(SATURDAY, "18:00", "02:00", true, 1, 1);
        var earlier = shift(SATURDAY, "13:00", "17:00", false, 1, 1); // 4h → 12h on Saturday

        verifier.verifyThat(ShiftScheduleConstraintProvider::maximumDailyHoursMustNotBeExceeded)
                .given(WEIGHTS, slot(bar, 0, anna), slot(earlier, 0, anna))
                .penalizesBy(120);
    }
}
