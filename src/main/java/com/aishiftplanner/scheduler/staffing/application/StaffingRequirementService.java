package com.aishiftplanner.scheduler.staffing.application;

import com.aishiftplanner.scheduler.audit.application.AuditService;
import com.aishiftplanner.scheduler.audit.domain.AuditAction;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.employee.application.SkillService;
import com.aishiftplanner.scheduler.organization.application.DepartmentService;
import com.aishiftplanner.scheduler.organization.domain.Department;
import com.aishiftplanner.scheduler.planning.application.PlanningPeriodService;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.schedule.domain.Shift;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import com.aishiftplanner.scheduler.staffing.api.StaffingDtos.CreateStaffingRequirementRequest;
import com.aishiftplanner.scheduler.staffing.api.StaffingDtos.GenerateShiftsResponse;
import com.aishiftplanner.scheduler.staffing.api.StaffingDtos.StaffingRequirementResponse;
import com.aishiftplanner.scheduler.staffing.domain.StaffingRequirement;
import com.aishiftplanner.scheduler.staffing.infrastructure.StaffingRequirementRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffingRequirementService {

    private final StaffingRequirementRepository requirementRepository;
    private final ShiftRepository shiftRepository;
    private final PlanningPeriodService planningPeriodService;
    private final DepartmentService departmentService;
    private final SkillService skillService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    public StaffingRequirementService(
            StaffingRequirementRepository requirementRepository,
            ShiftRepository shiftRepository,
            PlanningPeriodService planningPeriodService,
            DepartmentService departmentService,
            SkillService skillService,
            CurrentUserProvider currentUser,
            AuditService auditService) {
        this.requirementRepository = requirementRepository;
        this.shiftRepository = shiftRepository;
        this.planningPeriodService = planningPeriodService;
        this.departmentService = departmentService;
        this.skillService = skillService;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StaffingRequirementResponse> listForPeriod(UUID planningPeriodId) {
        planningPeriodService.loadInTenant(planningPeriodId);
        return requirementRepository
                .findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(planningPeriodId)
                .stream()
                .map(StaffingRequirementService::toResponse)
                .toList();
    }

    @Transactional
    public StaffingRequirementResponse create(
            UUID planningPeriodId, CreateStaffingRequirementRequest request) {

        PlanningPeriod period = planningPeriodService.loadInTenant(planningPeriodId);
        Department department = departmentService.loadInTenant(request.departmentId());

        if (!department.getLocationId().equals(period.getLocationId())) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "The department belongs to a different location than this planning period.");
        }
        if (!period.covers(request.date())) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "Date " + request.date() + " lies outside the planning period.");
        }
        validateStaffCounts(request);
        validateTimes(request);
        skillService.requireAllExistInTenant(
                request.requiredSkills() == null ? List.of() : request.requiredSkills().keySet());
        validateSkillCountsFitTheBlock(request);

        StaffingRequirement requirement = new StaffingRequirement(
                period.getOrganizationId(),
                period.getLocationId(),
                request.departmentId(),
                planningPeriodId,
                request.date(),
                request.startTime(),
                request.endTime(),
                request.crossesMidnight());
        requirement.setMinimumStaff(request.minimumStaff());
        requirement.setPreferredStaff(request.preferredStaff());
        requirement.setMaximumStaff(request.maximumStaff());
        requirement.setRequiredSkills(request.requiredSkills() == null ? Map.of() : request.requiredSkills());

        StaffingRequirement saved = requirementRepository.save(requirement);
        auditService.record(
                AuditAction.STAFFING_REQUIREMENT_CHANGED,
                "StaffingRequirement",
                saved.getId(),
                Map.of(
                        "planningPeriodId", planningPeriodId.toString(),
                        "date", saved.getDate().toString(),
                        "departmentId", saved.getDepartmentId().toString(),
                        "minimumStaff", String.valueOf(saved.getMinimumStaff())));
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID requirementId) {
        StaffingRequirement requirement = requirementRepository
                .findByIdAndOrganizationId(requirementId, currentUser.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Staffing requirement not found."));
        requirementRepository.delete(requirement);
        auditService.record(
                AuditAction.STAFFING_REQUIREMENT_CHANGED,
                "StaffingRequirement",
                requirementId,
                Map.of("operation", "DELETED"));
    }

    /**
     * Turns every requirement of a planning period into concrete shifts.
     *
     * <p>Regenerating replaces the period's shifts wholesale rather than trying to reconcile
     * them. Reconciliation sounds friendlier but is where subtle bugs live: a shift whose
     * requirement moved by an hour is not "the same shift, edited" from the solver's point of
     * view, and half-updated shift sets produce schedules nobody can explain. Managers who
     * want a hand-tuned shift preserved pin the assignment instead.
     *
     * <p>Staffing target is {@code preferredStaff}: the minimum is the floor the hard
     * constraints defend, while the preferred count is what the business actually wants on
     * the floor. Generating only the minimum would make "adequately staffed" indistinguishable
     * from "barely legal".
     */
    @Transactional
    public GenerateShiftsResponse generateShifts(UUID planningPeriodId) {
        PlanningPeriod period = planningPeriodService.loadInTenant(planningPeriodId);
        List<StaffingRequirement> requirements =
                requirementRepository.findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(planningPeriodId);

        if (requirements.isEmpty()) {
            throw ApiException.badRequest(
                    ErrorCode.PLANNING_PERIOD_NOT_READY,
                    "Define at least one staffing requirement before generating shifts.");
        }

        shiftRepository.deleteAllByPlanningPeriodId(planningPeriodId);
        shiftRepository.flush();

        List<Shift> shifts = new ArrayList<>(requirements.size());
        for (StaffingRequirement requirement : requirements) {
            Shift shift = new Shift(
                    period.getOrganizationId(),
                    requirement.getLocationId(),
                    requirement.getDepartmentId(),
                    planningPeriodId,
                    requirement.getDate(),
                    requirement.getStartTime(),
                    requirement.getEndTime(),
                    requirement.isCrossesMidnight(),
                    requirement.getPreferredStaff());
            shift.setMinimumEmployees(requirement.getMinimumStaff());
            shift.setRequirementId(requirement.getId());
            shift.setRequiredSkills(requirement.getRequiredSkills());
            shifts.add(shift);
        }
        List<Shift> saved = shiftRepository.saveAll(shifts);

        auditService.record(
                AuditAction.STAFFING_REQUIREMENT_CHANGED,
                "PlanningPeriod",
                planningPeriodId,
                Map.of(
                        "operation", "SHIFTS_GENERATED",
                        "requirements", String.valueOf(requirements.size()),
                        "shifts", String.valueOf(saved.size())));

        return new GenerateShiftsResponse(requirements.size(), saved.size());
    }

    private void validateStaffCounts(CreateStaffingRequirementRequest request) {
        if (request.preferredStaff() < request.minimumStaff()
                || request.maximumStaff() < request.preferredStaff()) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "Staff counts must satisfy minimumStaff ≤ preferredStaff ≤ maximumStaff.");
        }
    }

    private void validateTimes(CreateStaffingRequirementRequest request) {
        if (!request.crossesMidnight() && !request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "endTime must be after startTime, or crossesMidnight must be set.");
        }
        if (request.crossesMidnight() && request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "crossesMidnight is set but endTime is later than startTime on the same day.");
        }
    }

    /**
     * Rejects skill demands that cannot fit in the block.
     *
     * <p>"3 people, of whom 2× BAR and 2× CLOSING" is satisfiable (someone holds both), but
     * "3 people, of whom 4× BAR" is not, ever. Catching it here turns an unsolvable plan into
     * an immediate, fixable error message.
     */
    private void validateSkillCountsFitTheBlock(CreateStaffingRequirementRequest request) {
        if (request.requiredSkills() == null) {
            return;
        }
        for (Map.Entry<UUID, Integer> entry : request.requiredSkills().entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 1) {
                throw ApiException.badRequest(
                        ErrorCode.VALIDATION_FAILED, "Each required skill count must be at least 1.");
            }
            if (entry.getValue() > request.maximumStaff()) {
                throw ApiException.badRequest(
                        ErrorCode.VALIDATION_FAILED,
                        "A skill is required " + entry.getValue()
                                + " times but at most " + request.maximumStaff()
                                + " people can be assigned to this block.");
            }
        }
    }

    static StaffingRequirementResponse toResponse(StaffingRequirement requirement) {
        return new StaffingRequirementResponse(
                requirement.getId(),
                requirement.getOrganizationId(),
                requirement.getLocationId(),
                requirement.getDepartmentId(),
                requirement.getPlanningPeriodId(),
                requirement.getDate(),
                requirement.getStartTime(),
                requirement.getEndTime(),
                requirement.isCrossesMidnight(),
                requirement.getMinimumStaff(),
                requirement.getPreferredStaff(),
                requirement.getMaximumStaff(),
                requirement.window().hours(),
                requirement.getRequiredSkills());
    }
}
