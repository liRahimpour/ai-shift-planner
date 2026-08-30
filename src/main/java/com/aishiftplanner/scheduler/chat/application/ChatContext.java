package com.aishiftplanner.scheduler.chat.application;

import com.aishiftplanner.scheduler.schedule.domain.PlanningStrategy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Trusted, server-resolved context for one chat request.
 *
 * <p>The model never chooses these values. The planning period comes from the UI request and is
 * resolved against the authenticated user's organization before a tool can run. Keeping this
 * context separate from model-generated tool arguments prevents a model (or prompt injection)
 * from switching the query to another planning period or tenant.
 */
public record ChatContext(
        UUID organizationId,
        UUID planningPeriodId,
        LocalDate startDate,
        LocalDate endDate,
        UUID locationId,
        String locationName,
        ZoneId timezone,
        PlanningStrategy selectedStrategy) {

    private static final long MAX_ENUMERATED_DAYS = 31;

    public ChatContext {
        Objects.requireNonNull(organizationId, "organizationId");

        if (planningPeriodId == null) {
            if (startDate != null
                    || endDate != null
                    || locationId != null
                    || locationName != null
                    || timezone != null
                    || selectedStrategy != null) {
                throw new IllegalArgumentException(
                        "A chat context without a planning period cannot contain period metadata.");
            }
        } else {
            Objects.requireNonNull(startDate, "startDate");
            Objects.requireNonNull(endDate, "endDate");
            Objects.requireNonNull(locationId, "locationId");
            Objects.requireNonNull(locationName, "locationName");
            Objects.requireNonNull(timezone, "timezone");
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("Planning period end date must not be before start date.");
            }
        }
    }

    public static ChatContext withoutPlanningPeriod(UUID organizationId) {
        return new ChatContext(organizationId, null, null, null, null, null, null, null);
    }

    public boolean hasPlanningPeriod() {
        return planningPeriodId != null;
    }

    public boolean hasSelectedSchedule() {
        return selectedStrategy != null;
    }

    public boolean covers(LocalDate date) {
        return hasPlanningPeriod()
                && date != null
                && !date.isBefore(startDate)
                && !date.isAfter(endDate);
    }

    /**
     * A deterministic date-to-weekday index for the model.
     *
     * <p>For normal weekly/bi-weekly planning periods this removes date arithmetic from the
     * model entirely: "Samstag" can be matched to the one SATURDAY row. Very long periods are
     * deliberately not expanded into an unbounded prompt; in that case the model is told to
     * ask for an exact date when a weekday would be ambiguous.
     */
    public String dateIndexForPrompt() {
        if (!hasPlanningPeriod()) {
            return "(no planning period)";
        }

        long dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (dayCount > MAX_ENUMERATED_DAYS) {
            return "(period contains " + dayCount
                    + " days; do not infer a date from a weekday alone — ask for an exact date)";
        }

        return startDate
                .datesUntil(endDate.plusDays(1))
                .map(date -> date + " " + date.getDayOfWeek())
                .collect(Collectors.joining("\n"));
    }
}
