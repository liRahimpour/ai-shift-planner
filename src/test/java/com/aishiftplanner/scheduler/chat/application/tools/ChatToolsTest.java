package com.aishiftplanner.scheduler.chat.application.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.domain.Role;
import com.aishiftplanner.scheduler.chat.application.ChatContext;
import com.aishiftplanner.scheduler.chat.application.ChatTool;
import com.aishiftplanner.scheduler.chat.application.ScheduleQueryService;
import com.aishiftplanner.scheduler.schedule.domain.PlanningStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatToolsTest {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID PERIOD = UUID.randomUUID();

    private static AuthenticatedUser manager() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                ORGANIZATION,
                "manager@example.com",
                "Mia Manager",
                EnumSet.of(Role.SHIFT_MANAGER),
                true);
    }

    private static ChatContext context(PlanningStrategy selectedStrategy) {
        return new ChatContext(
                ORGANIZATION,
                PERIOD,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 13),
                UUID.randomUUID(),
                "Mainz",
                ZoneId.of("Europe/Berlin"),
                selectedStrategy);
    }

    private static ChatTool scheduleTool(ScheduleQueryService queries) {
        return new ChatTools().getScheduleForDateTool(queries, new ObjectMapper());
    }

    @Test
    void scheduleToolDoesNotExposePlanningPeriodIdToTheModel() {
        ScheduleQueryService queries = Mockito.mock(ScheduleQueryService.class);
        ChatTool tool = scheduleTool(queries);

        assertThat(tool.spec().parameters())
                .extracting(parameter -> parameter.name())
                .containsExactly("date", "department");
    }

    @Test
    void scheduleToolReturnsARealDomainStateWhenNoScheduleIsSelected() {
        ScheduleQueryService queries = Mockito.mock(ScheduleQueryService.class);
        ChatTool tool = scheduleTool(queries);

        String result = tool.execute(
                manager(),
                context(null),
                Map.of("date", "2026-09-12", "department", "Bar"));

        assertThat(result).contains("NO_SELECTED_SCHEDULE");
        assertThat(result).contains("select one of the schedule proposals");
        verifyNoInteractions(queries);
    }

    @Test
    void scheduleToolUsesTrustedPeriodEvenIfModelInventsAnotherPeriodId() {
        ScheduleQueryService queries = Mockito.mock(ScheduleQueryService.class);
        ChatTool tool = scheduleTool(queries);
        UUID inventedPeriod = UUID.randomUUID();
        LocalDate saturday = LocalDate.of(2026, 9, 12);
        when(queries.scheduleForDate(PERIOD, saturday, "Bar")).thenReturn(List.of());

        String result = tool.execute(
                manager(),
                context(PlanningStrategy.BALANCED),
                Map.of(
                        "planningPeriodId", inventedPeriod.toString(),
                        "date", saturday.toString(),
                        "department", "Bar"));

        assertThat(result).isEqualTo("[]");
        verify(queries).scheduleForDate(PERIOD, saturday, "Bar");
        verify(queries, never()).scheduleForDate(inventedPeriod, saturday, "Bar");
    }

    @Test
    void scheduleToolRejectsDatesOutsideTheOpenPlanningPeriod() {
        ScheduleQueryService queries = Mockito.mock(ScheduleQueryService.class);
        ChatTool tool = scheduleTool(queries);

        String result = tool.execute(
                manager(),
                context(PlanningStrategy.BALANCED),
                Map.of("date", "2026-09-19", "department", "Bar"));

        assertThat(result).contains("DATE_OUTSIDE_PLANNING_PERIOD");
        assertThat(result).contains("2026-09-07").contains("2026-09-13");
        verifyNoInteractions(queries);
    }
}
