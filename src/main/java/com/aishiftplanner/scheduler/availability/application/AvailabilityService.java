package com.aishiftplanner.scheduler.availability.application;

import com.aishiftplanner.scheduler.audit.application.AuditService;
import com.aishiftplanner.scheduler.audit.domain.AuditAction;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.AvailabilityResponse;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.AvailabilityWindowRequest;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.SubmitAvailabilityRequest;
import com.aishiftplanner.scheduler.availability.domain.Availability;
import com.aishiftplanner.scheduler.availability.infrastructure.AvailabilityRepository;
import com.aishiftplanner.scheduler.employee.application.EmployeeService;
import com.aishiftplanner.scheduler.employee.domain.Employee;
import com.aishiftplanner.scheduler.planning.application.PlanningPeriodService;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final PlanningPeriodService planningPeriodService;
    private final EmployeeService employeeService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final Clock clock;

    public AvailabilityService(
            AvailabilityRepository availabilityRepository,
            PlanningPeriodService planningPeriodService,
            EmployeeService employeeService,
            CurrentUserProvider currentUser,
            AuditService auditService,
            Clock clock) {
        this.availabilityRepository = availabilityRepository;
        this.planningPeriodService = planningPeriodService;
        this.employeeService = employeeService;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Replaces the calling employee's entire submission for a planning period.
     *
     * <p>Refuses once the deadline has passed. That refusal is the whole point of the
     * deadline: without it, a schedule can be invalidated by a submission that arrives after
     * planning has already started, and the manager has no way to know their inputs changed
     * underneath them. Managers can deliberately reopen the window
     * ({@code PlanningPeriodService.changeDeadline}), which is audited.
     */
    @Transactional
    public List<AvailabilityResponse> submitOwn(UUID planningPeriodId, SubmitAvailabilityRequest request) {
        Employee employee = employeeService.requireOwnEmployeeRecord();
        return replaceSubmission(planningPeriodId, employee, request, false);
    }

    /**
     * Lets a manager record availability on an employee's behalf (phone call, no account,
     * correcting a mistake). Bypasses the deadline, because a manager overriding it is a
     * deliberate act — but it is recorded as such in the audit trail.
     */
    @Transactional
    public List<AvailabilityResponse> submitForEmployee(
            UUID planningPeriodId, UUID employeeId, SubmitAvailabilityRequest request) {
        AuthenticatedUser caller = currentUser.require();
        if (!caller.isManager()) {
            throw ApiException.forbidden("Only managers may submit availability for another employee.");
        }
        Employee employee = employeeService.loadInTenant(employeeId);
        return replaceSubmission(planningPeriodId, employee, request, true);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listOwn(UUID planningPeriodId) {
        Employee employee = employeeService.requireOwnEmployeeRecord();
        planningPeriodService.loadInTenant(planningPeriodId);
        return availabilityRepository
                .findAllByPlanningPeriodIdAndEmployeeIdOrderByDateAscStartTimeAsc(
                        planningPeriodId, employee.getId())
                .stream()
                .map(AvailabilityService::toResponse)
                .toList();
    }

    /** Manager view: everything submitted for a period. */
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listForPeriod(UUID planningPeriodId) {
        AuthenticatedUser caller = currentUser.require();
        if (!caller.isManager()) {
            throw ApiException.forbidden("Only managers may view all submissions.");
        }
        planningPeriodService.loadInTenant(planningPeriodId);
        return availabilityRepository
                .findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(planningPeriodId)
                .stream()
                .map(AvailabilityService::toResponse)
                .toList();
    }

    private List<AvailabilityResponse> replaceSubmission(
            UUID planningPeriodId,
            Employee employee,
            SubmitAvailabilityRequest request,
            boolean managerOverride) {

        PlanningPeriod period = planningPeriodService.loadInTenant(planningPeriodId);

        if (!managerOverride && !period.acceptsAvailabilityAt(clock.instant())) {
            throw new ApiException(
                    ErrorCode.AVAILABILITY_DEADLINE_PASSED,
                    HttpStatus.CONFLICT,
                    "The availability deadline for this planning period has passed.");
        }
        if (!employee.isClearedForLocation(period.getLocationId())) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "This employee is not cleared to work at the location of this planning period.");
        }

        List<Availability> replacements = new ArrayList<>();
        for (AvailabilityWindowRequest window : request.windows()) {
            validateWindow(period, window);
            replacements.add(new Availability(
                    period.getOrganizationId(),
                    planningPeriodId,
                    employee.getId(),
                    window.date(),
                    window.availabilityType(),
                    window.startTime(),
                    window.endTime()));
        }
        rejectOverlaps(replacements);

        availabilityRepository.deleteAllByPlanningPeriodIdAndEmployeeId(planningPeriodId, employee.getId());
        // Flush the delete before inserting the replacements so the two do not interleave
        // and trip a constraint on a row that is about to disappear.
        availabilityRepository.flush();
        List<Availability> saved = availabilityRepository.saveAll(replacements);

        auditService.record(
                AuditAction.AVAILABILITY_CHANGED,
                "Availability",
                employee.getId(),
                Map.of(
                        "planningPeriodId", planningPeriodId.toString(),
                        "windowCount", String.valueOf(saved.size()),
                        "managerOverride", String.valueOf(managerOverride),
                        "afterDeadline", String.valueOf(period.deadlineHasPassed(clock.instant()))));

        return saved.stream().map(AvailabilityService::toResponse).toList();
    }

    private void validateWindow(PlanningPeriod period, AvailabilityWindowRequest window) {
        if (!period.covers(window.date())) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "Date " + window.date() + " lies outside the planning period.");
        }
        boolean hasStart = window.startTime() != null;
        boolean hasEnd = window.endTime() != null;
        if (hasStart != hasEnd) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "startTime and endTime must either both be set or both be omitted.");
        }
        if (hasStart && !window.endTime().isAfter(window.startTime())) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED, "endTime must be after startTime.");
        }
    }

    /**
     * Rejects contradictory windows on the same day.
     *
     * <p>"Available 10:00–14:00" and "Unavailable 12:00–16:00" on the same date is not a
     * preference the solver can weigh — it is a statement and its negation. Catching it at
     * submission time gives the employee a chance to fix it; catching it during planning
     * would mean a manager discovering it hours later with no way to ask.
     */
    private void rejectOverlaps(List<Availability> windows) {
        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                Availability a = windows.get(i);
                Availability b = windows.get(j);
                if (!a.getDate().equals(b.getDate())) {
                    continue;
                }
                boolean overlap = a.isWholeDay()
                        || b.isWholeDay()
                        || a.overlaps(b.getStartTime(), b.getEndTime());
                if (overlap) {
                    throw ApiException.badRequest(
                            ErrorCode.VALIDATION_FAILED,
                            "Overlapping availability windows on " + a.getDate()
                                    + ". Each part of a day may only be declared once.");
                }
            }
        }
    }

    static AvailabilityResponse toResponse(Availability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getPlanningPeriodId(),
                availability.getEmployeeId(),
                availability.getDate(),
                availability.getAvailabilityType(),
                availability.getStartTime(),
                availability.getEndTime());
    }
}
