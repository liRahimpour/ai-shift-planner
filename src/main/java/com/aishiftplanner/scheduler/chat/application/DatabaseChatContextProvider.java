package com.aishiftplanner.scheduler.chat.application;

import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.organization.domain.Location;
import com.aishiftplanner.scheduler.organization.infrastructure.LocationRepository;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningPeriodRepository;
import com.aishiftplanner.scheduler.schedule.domain.Schedule;
import com.aishiftplanner.scheduler.schedule.infrastructure.ScheduleRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads chat context from the database before the model is involved.
 *
 * <p>All lookups are tenant-scoped. A planning-period id supplied by the browser is treated as
 * untrusted input and must resolve inside the authenticated organization before it can become
 * part of a tool call.
 */
@Service
public class DatabaseChatContextProvider implements ChatContextProvider {

    private final PlanningPeriodRepository planningPeriodRepository;
    private final LocationRepository locationRepository;
    private final ScheduleRepository scheduleRepository;

    public DatabaseChatContextProvider(
            PlanningPeriodRepository planningPeriodRepository,
            LocationRepository locationRepository,
            ScheduleRepository scheduleRepository) {
        this.planningPeriodRepository = planningPeriodRepository;
        this.locationRepository = locationRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ChatContext resolve(AuthenticatedUser user, String rawPlanningPeriodId) {
        if (rawPlanningPeriodId == null || rawPlanningPeriodId.isBlank()) {
            return ChatContext.withoutPlanningPeriod(user.organizationId());
        }

        UUID planningPeriodId;
        try {
            planningPeriodId = UUID.fromString(rawPlanningPeriodId.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "planningPeriodId must be a valid UUID.");
        }

        PlanningPeriod period = planningPeriodRepository
                .findByIdAndOrganizationId(planningPeriodId, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Planning period not found."));

        Location location = locationRepository
                .findByIdAndOrganizationId(period.getLocationId(), user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Location for planning period not found."));

        Optional<Schedule> selectedSchedule = scheduleRepository
                .findByPlanningPeriodIdAndOrganizationIdAndSelectedTrue(
                        planningPeriodId, user.organizationId());

        return new ChatContext(
                user.organizationId(),
                planningPeriodId,
                period.getStartDate(),
                period.getEndDate(),
                period.getLocationId(),
                location.getName(),
                location.zoneId(),
                selectedSchedule.map(Schedule::getStrategy).orElse(null));
    }
}
