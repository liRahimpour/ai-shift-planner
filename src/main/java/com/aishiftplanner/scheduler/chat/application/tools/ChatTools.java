package com.aishiftplanner.scheduler.chat.application.tools;

import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.chat.application.ChatContext;
import com.aishiftplanner.scheduler.chat.application.ChatTool;
import com.aishiftplanner.scheduler.chat.application.ScheduleQueryService;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The concrete chat tools.
 *
 * <p>Model output is untrusted. The planning period is intentionally not a model parameter:
 * it comes from {@link ChatContext}, which was resolved against the authenticated tenant before
 * the tool loop started. This keeps the model useful for intent/argument extraction without
 * letting it choose the data scope.
 */
@Configuration
public class ChatTools {

    /** Base class holding shared argument parsing, context checks and JSON serialization. */
    abstract static class BaseTool implements ChatTool {

        protected final ScheduleQueryService queries;
        protected final ObjectMapper objectMapper;

        BaseTool(ScheduleQueryService queries, ObjectMapper objectMapper) {
            this.queries = queries;
            this.objectMapper = objectMapper;
        }

        protected String json(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException ex) {
                return "{\"errorCode\":\"SERIALIZATION_ERROR\",\"error\":\"The result could not be serialized.\"}";
            }
        }

        protected String error(String code, String message) {
            return json(Map.of("errorCode", code, "error", message));
        }

        protected Optional<UUID> uuid(Map<String, String> arguments, String key) {
            String raw = arguments.get(key);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }

        protected Optional<LocalDate> date(Map<String, String> arguments, String key) {
            String raw = arguments.get(key);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(LocalDate.parse(raw.trim()));
            } catch (DateTimeParseException ex) {
                return Optional.empty();
            }
        }

        protected boolean hasArgument(Map<String, String> arguments, String key) {
            String raw = arguments.get(key);
            return raw != null && !raw.isBlank();
        }

        protected Optional<String> requirePlanningPeriod(ChatContext context) {
            if (context.hasPlanningPeriod()) {
                return Optional.empty();
            }
            return Optional.of(error(
                    "NO_PLANNING_PERIOD",
                    "No planning period is open in this chat. Ask the user to open a planning period first."));
        }

        protected Optional<String> requireSelectedSchedule(ChatContext context) {
            Optional<String> periodError = requirePlanningPeriod(context);
            if (periodError.isPresent()) {
                return periodError;
            }
            if (context.hasSelectedSchedule()) {
                return Optional.empty();
            }
            return Optional.of(error(
                    "NO_SELECTED_SCHEDULE",
                    "No schedule has been selected for this planning period yet. "
                            + "Ask the manager to select one of the schedule proposals first."));
        }

        protected Optional<String> requireDateInPeriod(ChatContext context, LocalDate requestedDate) {
            Optional<String> periodError = requirePlanningPeriod(context);
            if (periodError.isPresent()) {
                return periodError;
            }
            if (context.covers(requestedDate)) {
                return Optional.empty();
            }
            return Optional.of(error(
                    "DATE_OUTSIDE_PLANNING_PERIOD",
                    "The requested date " + requestedDate + " is outside the open planning period "
                            + context.startDate() + " to " + context.endDate() + "."));
        }
    }

    // --- getScheduleForDate --------------------------------------------------

    @Bean
    ChatTool getScheduleForDateTool(ScheduleQueryService queries, ObjectMapper objectMapper) {
        return new BaseTool(queries, objectMapper) {
            @Override
            public String name() {
                return "getScheduleForDate";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Who is scheduled on a given date in the open planning period, optionally "
                                + "limited to one department such as Bar, Küche or Theke.",
                        List.of(
                                AiToolSpec.Parameter.requiredString("date", "The date, as YYYY-MM-DD."),
                                AiToolSpec.Parameter.optionalString(
                                        "department", "Department name, e.g. Bar. Omit for all departments.")));
            }

            @Override
            public boolean isPermittedFor(AuthenticatedUser user) {
                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user, ChatContext context, Map<String, String> arguments) {
                Optional<String> contextError = requireSelectedSchedule(context);
                if (contextError.isPresent()) {
                    return contextError.get();
                }

                LocalDate requestedDate = date(arguments, "date").orElse(null);
                if (requestedDate == null) {
                    return error("INVALID_TOOL_ARGUMENTS", "A date as YYYY-MM-DD is required.");
                }

                Optional<String> dateError = requireDateInPeriod(context, requestedDate);
                if (dateError.isPresent()) {
                    return dateError.get();
                }

                return json(queries.scheduleForDate(
                        context.planningPeriodId(), requestedDate, arguments.get("department")));
            }
        };
    }

    // --- getEmployeeHours ----------------------------------------------------

    @Bean
    ChatTool getEmployeeHoursTool(ScheduleQueryService queries, ObjectMapper objectMapper) {
        return new BaseTool(queries, objectMapper) {
            @Override
            public String name() {
                return "getEmployeeHours";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Scheduled hours per employee against contract hours in the selected schedule, "
                                + "for the whole team or one employee.",
                        List.of(AiToolSpec.Parameter.optionalString(
                                "employeeId", "One employee's UUID. Omit for the whole team.")));
            }

            @Override
            public boolean isPermittedFor(AuthenticatedUser user) {
                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user, ChatContext context, Map<String, String> arguments) {
                Optional<String> contextError = requireSelectedSchedule(context);
                if (contextError.isPresent()) {
                    return contextError.get();
                }

                UUID employeeId = uuid(arguments, "employeeId").orElse(null);
                if (hasArgument(arguments, "employeeId") && employeeId == null) {
                    return error("INVALID_TOOL_ARGUMENTS", "employeeId must be a valid UUID when provided.");
                }

                return json(queries.employeeHours(context.planningPeriodId(), employeeId));
            }
        };
    }

    // --- getUnderstaffedShifts -----------------------------------------------

    @Bean
    ChatTool getUnderstaffedShiftsTool(ScheduleQueryService queries, ObjectMapper objectMapper) {
        return new BaseTool(queries, objectMapper) {
            @Override
            public String name() {
                return "getUnderstaffedShifts";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Shifts that are below their minimum staffing in the selected schedule.",
                        List.of());
            }

            @Override
            public boolean isPermittedFor(AuthenticatedUser user) {
                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user, ChatContext context, Map<String, String> arguments) {
                Optional<String> contextError = requireSelectedSchedule(context);
                if (contextError.isPresent()) {
                    return contextError.get();
                }
                return json(queries.understaffedShifts(context.planningPeriodId()));
            }
        };
    }

    // --- findReplacementCandidates -------------------------------------------

    @Bean
    ChatTool findReplacementCandidatesTool(ScheduleQueryService queries, ObjectMapper objectMapper) {
        return new BaseTool(queries, objectMapper) {
            @Override
            public String name() {
                return "findReplacementCandidates";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Ranked replacements for a shift when someone calls in sick. Each candidate "
                                + "comes with the facts behind the ranking: availability, skills, "
                                + "resulting hours, overtime, rest time and cost.",
                        List.of(AiToolSpec.Parameter.requiredString(
                                "shiftId", "The UUID of the shift that needs covering.")));
            }

            @Override
            public boolean isPermittedFor(AuthenticatedUser user) {
                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user, ChatContext context, Map<String, String> arguments) {
                Optional<String> contextError = requireSelectedSchedule(context);
                if (contextError.isPresent()) {
                    return contextError.get();
                }

                UUID shiftId = uuid(arguments, "shiftId").orElse(null);
                if (shiftId == null) {
                    return error("INVALID_TOOL_ARGUMENTS", "A valid shiftId is required.");
                }
                try {
                    return json(queries.findReplacements(context.planningPeriodId(), shiftId));
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    return error("QUERY_NOT_AVAILABLE", "The replacement query could not be completed for this schedule.");
                }
            }
        };
    }

    // --- getEmployeesWhoRequestedOff -----------------------------------------

    @Bean
    ChatTool getEmployeesWhoRequestedOffTool(ScheduleQueryService queries, ObjectMapper objectMapper) {
        return new BaseTool(queries, objectMapper) {
            @Override
            public String name() {
                return "getEmployeesWhoRequestedOff";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Employees who marked themselves unavailable on a given date in the open planning period.",
                        List.of(AiToolSpec.Parameter.requiredString("date", "The date, as YYYY-MM-DD.")));
            }

            @Override
            public boolean isPermittedFor(AuthenticatedUser user) {
                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user, ChatContext context, Map<String, String> arguments) {
                Optional<String> periodError = requirePlanningPeriod(context);
                if (periodError.isPresent()) {
                    return periodError.get();
                }

                LocalDate requestedDate = date(arguments, "date").orElse(null);
                if (requestedDate == null) {
                    return error("INVALID_TOOL_ARGUMENTS", "A date as YYYY-MM-DD is required.");
                }

                Optional<String> dateError = requireDateInPeriod(context, requestedDate);
                if (dateError.isPresent()) {
                    return dateError.get();
                }

                return json(queries.employeesWhoRequestedOff(context.planningPeriodId(), requestedDate));
            }
        };
    }

    // --- getSchedulingExplanation --------------------------------------------

    @Bean
    ChatTool getSchedulingExplanationTool(ScheduleQueryService queries, ObjectMapper objectMapper) {
        return new BaseTool(queries, objectMapper) {
            @Override
            public String name() {
                return "getSchedulingExplanation";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "The facts behind one person's assignment to one shift: declared availability, "
                                + "required skills, scheduled hours and unavailable colleagues.",
                        List.of(
                                AiToolSpec.Parameter.requiredString("shiftId", "The shift's UUID."),
                                AiToolSpec.Parameter.requiredString("employeeId", "The employee's UUID.")));
            }

            @Override
            public boolean isPermittedFor(AuthenticatedUser user) {
                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user, ChatContext context, Map<String, String> arguments) {
                Optional<String> contextError = requireSelectedSchedule(context);
                if (contextError.isPresent()) {
                    return contextError.get();
                }

                UUID shiftId = uuid(arguments, "shiftId").orElse(null);
                UUID employeeId = uuid(arguments, "employeeId").orElse(null);
                if (shiftId == null || employeeId == null) {
                    return error(
                            "INVALID_TOOL_ARGUMENTS",
                            "A valid shiftId and employeeId are required.");
                }
                try {
                    return json(queries.explainAssignment(
                            context.planningPeriodId(), shiftId, employeeId));
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    return error("QUERY_NOT_AVAILABLE", "The assignment explanation could not be completed for this schedule.");
                }
            }
        };
    }

    // --- getMySchedule (the only employee-accessible tool) -------------------

    @Bean
    ChatTool getMyScheduleTool(
            ScheduleQueryService queries, ObjectMapper objectMapper, EmployeeRepository employeeRepository) {
        return new BaseTool(queries, objectMapper) {
            @Override
            public String name() {
                return "getMySchedule";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "The calling employee's own scheduled hours in the selected schedule.",
                        List.of());
            }

            @Override
            public boolean isPermittedFor(AuthenticatedUser user) {
                return true;
            }

            @Override
            public String execute(
                    AuthenticatedUser user, ChatContext context, Map<String, String> arguments) {
                Optional<String> contextError = requireSelectedSchedule(context);
                if (contextError.isPresent()) {
                    return contextError.get();
                }

                // The employee id comes from the authenticated principal, never from the model.
                UUID ownEmployeeId = employeeRepository
                        .findByUserId(user.userId())
                        .filter(employee -> user.organizationId().equals(employee.getOrganizationId()))
                        .map(employee -> employee.getId())
                        .orElse(null);
                if (ownEmployeeId == null) {
                    return error("NO_EMPLOYEE_RECORD", "No employee record is linked to your account.");
                }
                return json(queries.employeeHours(context.planningPeriodId(), ownEmployeeId));
            }
        };
    }
}
