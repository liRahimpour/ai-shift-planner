package com.aishiftplanner.scheduler.chat.application.tools;

import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.chat.application.ChatTool;
import com.aishiftplanner.scheduler.chat.application.ScheduleQueryService;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The concrete chat tools.
 *
 * <p>Collected in one file because they share one shape: parse untrusted arguments, refuse
 * anything that does not parse, call {@link ScheduleQueryService}, serialize the result.
 * Seeing them together also makes the permission column reviewable at a glance, which is the
 * property that matters most about this file.
 *
 * <p>Every tool here is manager-only except {@code getMySchedule}. That is not a UI
 * convenience: an employee's chat is offered a strictly smaller tool set, so there is no
 * prompt clever enough to make it return a colleague's hours or anyone's pay.
 */
@Configuration
public class ChatTools {

    /**
     * Base class holding the shared plumbing:
     * argument parsing and JSON serialization.
     */
    abstract static class BaseTool implements ChatTool {

        protected final ScheduleQueryService queries;
        protected final JsonMapper jsonMapper;

        BaseTool(
                ScheduleQueryService queries,
                JsonMapper jsonMapper) {

            this.queries = queries;
            this.jsonMapper = jsonMapper;
        }

        protected String json(Object value) {
            try {
                return jsonMapper.writeValueAsString(value);
            } catch (JacksonException ex) {
                return "{\"error\":\"The result could not be serialized.\"}";
            }
        }

        protected String error(String message) {
            return "{\"error\":" + quote(message) + "}";
        }

        private static String quote(String text) {
            return "\""
                    + text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    + "\"";
        }

        /**
         * Parses a UUID argument.
         *
         * <p>Model output is untrusted: a malformed id is answered with a plain error
         * the model can relay, not an exception that becomes a 500.
         */
        protected Optional<UUID> uuid(
                Map<String, String> arguments,
                String key) {

            String raw = arguments.get(key);

            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }

            try {
                return Optional.of(
                        UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }

        protected Optional<LocalDate> date(
                Map<String, String> arguments,
                String key) {

            String raw = arguments.get(key);

            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }

            try {
                return Optional.of(
                        LocalDate.parse(raw.trim()));
            } catch (DateTimeParseException ex) {
                return Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // getScheduleForDate
    // -------------------------------------------------------------------------

    @Bean
    ChatTool getScheduleForDateTool(
            ScheduleQueryService queries,
            JsonMapper jsonMapper) {

        return new BaseTool(queries, jsonMapper) {

            @Override
            public String name() {
                return "getScheduleForDate";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Who is scheduled on a given date, optionally limited to one department "
                                + "such as Bar, Küche or Theke.",
                        List.of(
                                AiToolSpec.Parameter.requiredString(
                                        "planningPeriodId",
                                        "The planning period's UUID."),
                                AiToolSpec.Parameter.requiredString(
                                        "date",
                                        "The date, as YYYY-MM-DD."),
                                AiToolSpec.Parameter.optionalString(
                                        "department",
                                        "Department name, e.g. Bar. Omit for all departments.")));
            }

            @Override
            public boolean isPermittedFor(
                    AuthenticatedUser user) {

                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user,
                    Map<String, String> arguments) {

                UUID periodId =
                        uuid(arguments, "planningPeriodId")
                                .orElse(null);

                LocalDate date =
                        date(arguments, "date")
                                .orElse(null);

                if (periodId == null || date == null) {
                    return error(
                            "A valid planningPeriodId and a date as YYYY-MM-DD are required.");
                }

                return json(
                        queries.scheduleForDate(
                                periodId,
                                date,
                                arguments.get("department")));
            }
        };
    }

    // -------------------------------------------------------------------------
    // getEmployeeHours
    // -------------------------------------------------------------------------

    @Bean
    ChatTool getEmployeeHoursTool(
            ScheduleQueryService queries,
            JsonMapper jsonMapper) {

        return new BaseTool(queries, jsonMapper) {

            @Override
            public String name() {
                return "getEmployeeHours";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Scheduled hours per employee against their contract hours, for the whole "
                                + "team or one person. Answers questions about who is working the most, "
                                + "who is below their contract hours, and how many hours someone has.",
                        List.of(
                                AiToolSpec.Parameter.requiredString(
                                        "planningPeriodId",
                                        "The planning period's UUID."),
                                AiToolSpec.Parameter.optionalString(
                                        "employeeId",
                                        "One employee's UUID. Omit for the whole team.")));
            }

            @Override
            public boolean isPermittedFor(
                    AuthenticatedUser user) {

                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user,
                    Map<String, String> arguments) {

                UUID periodId =
                        uuid(arguments, "planningPeriodId")
                                .orElse(null);

                if (periodId == null) {
                    return error(
                            "A valid planningPeriodId is required.");
                }

                return json(
                        queries.employeeHours(
                                periodId,
                                uuid(arguments, "employeeId")
                                        .orElse(null)));
            }
        };
    }

    // -------------------------------------------------------------------------
    // getUnderstaffedShifts
    // -------------------------------------------------------------------------

    @Bean
    ChatTool getUnderstaffedShiftsTool(
            ScheduleQueryService queries,
            JsonMapper jsonMapper) {

        return new BaseTool(queries, jsonMapper) {

            @Override
            public String name() {
                return "getUnderstaffedShifts";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Shifts that are below their minimum staffing in the selected schedule.",
                        List.of(
                                AiToolSpec.Parameter.requiredString(
                                        "planningPeriodId",
                                        "The planning period's UUID.")));
            }

            @Override
            public boolean isPermittedFor(
                    AuthenticatedUser user) {

                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user,
                    Map<String, String> arguments) {

                UUID periodId =
                        uuid(arguments, "planningPeriodId")
                                .orElse(null);

                if (periodId == null) {
                    return error(
                            "A valid planningPeriodId is required.");
                }

                return json(
                        queries.understaffedShifts(periodId));
            }
        };
    }

    // -------------------------------------------------------------------------
    // findReplacementCandidates
    // -------------------------------------------------------------------------

    @Bean
    ChatTool findReplacementCandidatesTool(
            ScheduleQueryService queries,
            JsonMapper jsonMapper) {

        return new BaseTool(queries, jsonMapper) {

            @Override
            public String name() {
                return "findReplacementCandidates";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Ranked replacements for a shift when someone calls in sick. Each candidate "
                                + "comes with the facts behind their ranking: availability, skills, "
                                + "resulting hours, overtime, rest time and cost.",
                        List.of(
                                AiToolSpec.Parameter.requiredString(
                                        "planningPeriodId",
                                        "The planning period's UUID."),
                                AiToolSpec.Parameter.requiredString(
                                        "shiftId",
                                        "The UUID of the shift that needs covering.")));
            }

            @Override
            public boolean isPermittedFor(
                    AuthenticatedUser user) {

                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user,
                    Map<String, String> arguments) {

                UUID periodId =
                        uuid(arguments, "planningPeriodId")
                                .orElse(null);

                UUID shiftId =
                        uuid(arguments, "shiftId")
                                .orElse(null);

                if (periodId == null || shiftId == null) {
                    return error(
                            "A valid planningPeriodId and shiftId are required.");
                }

                try {
                    return json(
                            queries.findReplacements(
                                    periodId,
                                    shiftId));
                } catch (IllegalArgumentException
                         | IllegalStateException ex) {

                    return error(ex.getMessage());
                }
            }
        };
    }

    // -------------------------------------------------------------------------
    // getEmployeesWhoRequestedOff
    // -------------------------------------------------------------------------

    @Bean
    ChatTool getEmployeesWhoRequestedOffTool(
            ScheduleQueryService queries,
            JsonMapper jsonMapper) {

        return new BaseTool(queries, jsonMapper) {

            @Override
            public String name() {
                return "getEmployeesWhoRequestedOff";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "Employees who marked themselves unavailable on a given date.",
                        List.of(
                                AiToolSpec.Parameter.requiredString(
                                        "planningPeriodId",
                                        "The planning period's UUID."),
                                AiToolSpec.Parameter.requiredString(
                                        "date",
                                        "The date, as YYYY-MM-DD.")));
            }

            @Override
            public boolean isPermittedFor(
                    AuthenticatedUser user) {

                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user,
                    Map<String, String> arguments) {

                UUID periodId =
                        uuid(arguments, "planningPeriodId")
                                .orElse(null);

                LocalDate date =
                        date(arguments, "date")
                                .orElse(null);

                if (periodId == null || date == null) {
                    return error(
                            "A valid planningPeriodId and a date as YYYY-MM-DD are required.");
                }

                return json(
                        queries.employeesWhoRequestedOff(
                                periodId,
                                date));
            }
        };
    }

    // -------------------------------------------------------------------------
    // getSchedulingExplanation
    // -------------------------------------------------------------------------

    @Bean
    ChatTool getSchedulingExplanationTool(
            ScheduleQueryService queries,
            JsonMapper jsonMapper) {

        return new BaseTool(queries, jsonMapper) {

            @Override
            public String name() {
                return "getSchedulingExplanation";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "The facts behind one person's assignment to one shift: what they declared, "
                                + "which skills they hold, their hours, and who else was unavailable. "
                                + "Use this to answer 'why does X work on Y?' - and answer ONLY from "
                                + "the facts returned.",
                        List.of(
                                AiToolSpec.Parameter.requiredString(
                                        "planningPeriodId",
                                        "The planning period's UUID."),
                                AiToolSpec.Parameter.requiredString(
                                        "shiftId",
                                        "The shift's UUID."),
                                AiToolSpec.Parameter.requiredString(
                                        "employeeId",
                                        "The employee's UUID.")));
            }

            @Override
            public boolean isPermittedFor(
                    AuthenticatedUser user) {

                return user.isManager();
            }

            @Override
            public String execute(
                    AuthenticatedUser user,
                    Map<String, String> arguments) {

                UUID periodId =
                        uuid(arguments, "planningPeriodId")
                                .orElse(null);

                UUID shiftId =
                        uuid(arguments, "shiftId")
                                .orElse(null);

                UUID employeeId =
                        uuid(arguments, "employeeId")
                                .orElse(null);

                if (periodId == null
                        || shiftId == null
                        || employeeId == null) {

                    return error(
                            "A valid planningPeriodId, shiftId and employeeId are required.");
                }

                try {
                    return json(
                            queries.explainAssignment(
                                    periodId,
                                    shiftId,
                                    employeeId));
                } catch (IllegalArgumentException
                         | IllegalStateException ex) {

                    return error(ex.getMessage());
                }
            }
        };
    }

    // -------------------------------------------------------------------------
    // getMySchedule
    // -------------------------------------------------------------------------

    @Bean
    ChatTool getMyScheduleTool(
            ScheduleQueryService queries,
            JsonMapper jsonMapper,
            EmployeeRepository employeeRepository) {

        return new BaseTool(queries, jsonMapper) {

            @Override
            public String name() {
                return "getMySchedule";
            }

            @Override
            public AiToolSpec spec() {
                return new AiToolSpec(
                        name(),
                        "The calling employee's own scheduled hours for a planning period.",
                        List.of(
                                AiToolSpec.Parameter.requiredString(
                                        "planningPeriodId",
                                        "The planning period's UUID.")));
            }

            @Override
            public boolean isPermittedFor(
                    AuthenticatedUser user) {

                return true;
            }

            @Override
            public String execute(
                    AuthenticatedUser user,
                    Map<String, String> arguments) {

                UUID periodId =
                        uuid(arguments, "planningPeriodId")
                                .orElse(null);

                if (periodId == null) {
                    return error(
                            "A valid planningPeriodId is required.");
                }

                /*
                 * The employee id comes from the authenticated principal,
                 * never from model output.
                 */
                UUID ownEmployeeId =
                        employeeRepository
                                .findByUserId(user.userId())
                                .map(employee -> employee.getId())
                                .orElse(null);

                if (ownEmployeeId == null) {
                    return error(
                            "No employee record is linked to your account.");
                }

                return json(
                        queries.employeeHours(
                                periodId,
                                ownEmployeeId));
            }
        };
    }
}