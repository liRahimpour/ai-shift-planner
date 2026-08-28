package com.aishiftplanner.scheduler.planning.application;

import com.aishiftplanner.scheduler.audit.application.AuditService;
import com.aishiftplanner.scheduler.audit.domain.AuditAction;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.availability.domain.CommentInterpretation;
import com.aishiftplanner.scheduler.availability.infrastructure.AvailabilityRepository;
import com.aishiftplanner.scheduler.availability.infrastructure.CommentInterpretationRepository;
import com.aishiftplanner.scheduler.availability.infrastructure.EmployeeCommentRepository;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import com.aishiftplanner.scheduler.organization.application.LocationService;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.ChangeDeadlineRequest;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.CreatePlanningPeriodRequest;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.PlanningPeriodResponse;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.PlanningPeriodSummaryResponse;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriodStatus;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningPeriodRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningPeriodService {

    private final PlanningPeriodRepository planningPeriodRepository;
    private final AvailabilityRepository availabilityRepository;
    private final EmployeeCommentRepository commentRepository;
    private final CommentInterpretationRepository interpretationRepository;
    private final EmployeeRepository employeeRepository;
    private final LocationService locationService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final Clock clock;

    public PlanningPeriodService(
            PlanningPeriodRepository planningPeriodRepository,
            AvailabilityRepository availabilityRepository,
            EmployeeCommentRepository commentRepository,
            CommentInterpretationRepository interpretationRepository,
            EmployeeRepository employeeRepository,
            LocationService locationService,
            CurrentUserProvider currentUser,
            AuditService auditService,
            Clock clock) {
        this.planningPeriodRepository = planningPeriodRepository;
        this.availabilityRepository = availabilityRepository;
        this.commentRepository = commentRepository;
        this.interpretationRepository = interpretationRepository;
        this.employeeRepository = employeeRepository;
        this.locationService = locationService;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlanningPeriodResponse> listForLocation(UUID locationId) {
        locationService.loadInTenant(locationId);
        return planningPeriodRepository
                .findAllByOrganizationIdAndLocationIdOrderByStartDateDesc(
                        currentUser.requireOrganizationId(), locationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PlanningPeriodResponse create(CreatePlanningPeriodRequest request) {
        var location = locationService.loadInTenant(request.locationId());

        if (request.endDate().isBefore(request.startDate())) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED, "endDate must not be before startDate.");
        }
        if (planningPeriodRepository.existsByLocationIdAndStartDate(
                request.locationId(), request.startDate())) {
            throw ApiException.conflict(
                    ErrorCode.ALREADY_EXISTS,
                    "A planning period starting on this date already exists for this location.");
        }
        // Anchored in the location's own zone, not UTC: "the week starts Monday" means
        // Monday 00:00 in Mainz, and getting this wrong by an hour or two around a DST
        // change would silently accept or reject deadlines near midnight.
        Instant periodStart = request.startDate().atStartOfDay(location.zoneId()).toInstant();
        if (!request.availabilityDeadline().isBefore(periodStart)) {
            // A deadline at or after the period begins means the schedule cannot be
            // published before the first shift it contains - almost always a typo.
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "availabilityDeadline must fall before the planning period starts.");
        }

        PlanningPeriod period = new PlanningPeriod(
                currentUser.requireOrganizationId(),
                request.locationId(),
                request.startDate(),
                request.endDate(),
                request.availabilityDeadline(),
                currentUser.require().userId());

        PlanningPeriod saved = planningPeriodRepository.save(period);
        auditService.record(
                AuditAction.PLANNING_PERIOD_CREATED,
                "PlanningPeriod",
                saved.getId(),
                Map.of(
                        "locationId", saved.getLocationId().toString(),
                        "startDate", saved.getStartDate().toString(),
                        "endDate", saved.getEndDate().toString(),
                        "availabilityDeadline", saved.getAvailabilityDeadline().toString()));
        return toResponse(saved);
    }

    /**
     * Reopens (or extends) the availability window.
     *
     * <p>Always audited: reopening a deadline changes what employees can still influence, and
     * "who reopened it, when, and from what to what" is exactly the question that gets asked
     * when a schedule turns out differently than someone expected.
     */
    @Transactional
    public PlanningPeriodResponse changeDeadline(UUID periodId, ChangeDeadlineRequest request) {
        PlanningPeriod period = loadInTenant(periodId);
        Instant previous = period.getAvailabilityDeadline();
        boolean wasClosed = period.getStatus() != PlanningPeriodStatus.OPEN_FOR_AVAILABILITY;

        period.reopenAvailability(request.availabilityDeadline());
        PlanningPeriod saved = planningPeriodRepository.save(period);

        auditService.record(
                wasClosed ? AuditAction.AVAILABILITY_REOPENED : AuditAction.AVAILABILITY_DEADLINE_CHANGED,
                "PlanningPeriod",
                periodId,
                Map.of(
                        "previousDeadline", previous.toString(),
                        "newDeadline", request.availabilityDeadline().toString(),
                        "previouslyClosed", String.valueOf(wasClosed)));
        return toResponse(saved);
    }

    @Transactional
    public PlanningPeriodResponse changeStatus(UUID periodId, PlanningPeriodStatus newStatus) {
        PlanningPeriod period = loadInTenant(periodId);
        PlanningPeriodStatus previous = period.getStatus();

        if (previous == newStatus) {
            return toResponse(period);
        }
        if (previous.isFinal()) {
            throw ApiException.conflict(
                    ErrorCode.CONFLICT, "An archived planning period cannot change status.");
        }

        period.setStatus(newStatus);
        PlanningPeriod saved = planningPeriodRepository.save(period);

        auditService.recordChange(
                AuditAction.PLANNING_PERIOD_STATUS_CHANGED,
                "PlanningPeriod",
                periodId,
                "status",
                previous,
                newStatus);
        return toResponse(saved);
    }

    /** The manager dashboard's headline numbers. */
    @Transactional(readOnly = true)
    public PlanningPeriodSummaryResponse summary(UUID periodId) {
        PlanningPeriod period = loadInTenant(periodId);
        UUID organizationId = period.getOrganizationId();

        long totalActive = employeeRepository.countByOrganizationIdAndLocationIdAndActiveTrue(
                organizationId, period.getLocationId());
        long submitted = availabilityRepository.countDistinctSubmittingEmployees(periodId);
        long comments = commentRepository.findAllByPlanningPeriodIdOrderByCreatedAtDesc(periodId).size();
        long pendingReviews = interpretationRepository
                .findAllByOrganizationIdAndReviewStatusOrderByConfidenceAsc(
                        organizationId, CommentInterpretation.ReviewStatus.PENDING)
                .size();

        return new PlanningPeriodSummaryResponse(
                toResponse(period),
                totalActive,
                submitted,
                Math.max(0, totalActive - submitted),
                comments,
                pendingReviews);
    }

    @Transactional(readOnly = true)
    public PlanningPeriod loadInTenant(UUID id) {
        return planningPeriodRepository
                .findByIdAndOrganizationId(id, currentUser.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Planning period not found."));
    }

    PlanningPeriodResponse toResponse(PlanningPeriod period) {
        return new PlanningPeriodResponse(
                period.getId(),
                period.getOrganizationId(),
                period.getLocationId(),
                period.getStartDate(),
                period.getEndDate(),
                period.getAvailabilityDeadline(),
                period.getStatus(),
                period.deadlineHasPassed(clock.instant()),
                period.getCreatedBy());
    }
}
