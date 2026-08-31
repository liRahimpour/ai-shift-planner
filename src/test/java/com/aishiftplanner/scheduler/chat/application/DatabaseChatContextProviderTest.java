package com.aishiftplanner.scheduler.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.domain.Role;
import com.aishiftplanner.scheduler.organization.domain.Location;
import com.aishiftplanner.scheduler.organization.infrastructure.LocationRepository;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningPeriodRepository;
import com.aishiftplanner.scheduler.schedule.domain.PlanningStrategy;
import com.aishiftplanner.scheduler.schedule.domain.Schedule;
import com.aishiftplanner.scheduler.schedule.infrastructure.ScheduleRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DatabaseChatContextProviderTest {

    private static AuthenticatedUser manager(UUID organizationId) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                organizationId,
                "manager@example.com",
                "Mia Manager",
                EnumSet.of(Role.SHIFT_MANAGER),
                true);
    }

    @Test
    void resolvesPeriodLocationAndSelectedScheduleInsideAuthenticatedTenant() {
        UUID organizationId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        AuthenticatedUser user = manager(organizationId);

        PlanningPeriod period = new PlanningPeriod(
                organizationId,
                locationId,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 13),
                Instant.parse("2026-09-06T16:00:00Z"),
                user.userId());
        Location location = new Location(organizationId, "Mainz", "Europe/Berlin");
        Schedule selected = new Schedule(organizationId, periodId, PlanningStrategy.BALANCED);
        selected.setSelected(true);

        PlanningPeriodRepository periods = Mockito.mock(PlanningPeriodRepository.class);
        LocationRepository locations = Mockito.mock(LocationRepository.class);
        ScheduleRepository schedules = Mockito.mock(ScheduleRepository.class);
        when(periods.findByIdAndOrganizationId(periodId, organizationId)).thenReturn(Optional.of(period));
        when(locations.findByIdAndOrganizationId(locationId, organizationId)).thenReturn(Optional.of(location));
        when(schedules.findByPlanningPeriodIdAndOrganizationIdAndSelectedTrue(periodId, organizationId))
                .thenReturn(Optional.of(selected));

        DatabaseChatContextProvider provider =
                new DatabaseChatContextProvider(periods, locations, schedules);

        ChatContext context = provider.resolve(user, periodId.toString());

        assertThat(context.organizationId()).isEqualTo(organizationId);
        assertThat(context.planningPeriodId()).isEqualTo(periodId);
        assertThat(context.startDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(context.endDate()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(context.locationName()).isEqualTo("Mainz");
        assertThat(context.timezone()).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(context.selectedStrategy()).isEqualTo(PlanningStrategy.BALANCED);

        verify(periods).findByIdAndOrganizationId(periodId, organizationId);
        verify(locations).findByIdAndOrganizationId(locationId, organizationId);
        verify(schedules).findByPlanningPeriodIdAndOrganizationIdAndSelectedTrue(periodId, organizationId);
        verify(schedules, never()).findByPlanningPeriodIdAndSelectedTrue(periodId);
    }

    @Test
    void malformedPlanningPeriodIdFailsBeforeAnyDatabaseLookup() {
        UUID organizationId = UUID.randomUUID();
        PlanningPeriodRepository periods = Mockito.mock(PlanningPeriodRepository.class);
        LocationRepository locations = Mockito.mock(LocationRepository.class);
        ScheduleRepository schedules = Mockito.mock(ScheduleRepository.class);
        DatabaseChatContextProvider provider =
                new DatabaseChatContextProvider(periods, locations, schedules);

        assertThatThrownBy(() -> provider.resolve(manager(organizationId), "not-a-uuid"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.getMessage()).contains("valid UUID");
                });

        verifyNoInteractions(periods, locations, schedules);
    }

    @Test
    void missingPlanningPeriodProducesContextWithoutDataScope() {
        UUID organizationId = UUID.randomUUID();
        PlanningPeriodRepository periods = Mockito.mock(PlanningPeriodRepository.class);
        LocationRepository locations = Mockito.mock(LocationRepository.class);
        ScheduleRepository schedules = Mockito.mock(ScheduleRepository.class);
        DatabaseChatContextProvider provider =
                new DatabaseChatContextProvider(periods, locations, schedules);

        ChatContext context = provider.resolve(manager(organizationId), null);

        assertThat(context.organizationId()).isEqualTo(organizationId);
        assertThat(context.hasPlanningPeriod()).isFalse();
        assertThat(context.hasSelectedSchedule()).isFalse();
        verifyNoInteractions(periods, locations, schedules);
    }
}
