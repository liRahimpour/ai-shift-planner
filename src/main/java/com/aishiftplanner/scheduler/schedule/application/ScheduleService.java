package com.aishiftplanner.scheduler.schedule.application;

import com.aishiftplanner.scheduler.audit.application.AuditService;
import com.aishiftplanner.scheduler.audit.domain.AuditAction;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.employee.application.EmployeeService;
import com.aishiftplanner.scheduler.employee.domain.Employee;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import com.aishiftplanner.scheduler.organization.infrastructure.DepartmentRepository;
import com.aishiftplanner.scheduler.planning.application.PlanningPeriodService;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriodStatus;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningPeriodRepository;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.AssignmentResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.MyScheduleResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ReassignResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ScheduleDetailResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ScheduleMetricsResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ScheduleSummaryResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ShiftWithAssignmentsResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ValidationResponse;
import com.aishiftplanner.scheduler.schedule.domain.Schedule;
import com.aishiftplanner.scheduler.schedule.domain.ScheduleStatus;
import com.aishiftplanner.scheduler.schedule.domain.Shift;
import com.aishiftplanner.scheduler.schedule.domain.ShiftAssignment;
import com.aishiftplanner.scheduler.schedule.domain.ShiftStatus;
import com.aishiftplanner.scheduler.schedule.infrastructure.ScheduleRepository;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftAssignmentRepository;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading proposals, selecting one, editing it by hand, pinning, and publishing. */
@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PlanningPeriodRepository planningPeriodRepository;
    private final PlanningPeriodService planningPeriodService;
    private final EmployeeService employeeService;
    private final ScheduleValidator validator;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            ShiftRepository shiftRepository,
            ShiftAssignmentRepository assignmentRepository,
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            PlanningPeriodRepository planningPeriodRepository,
            PlanningPeriodService planningPeriodService,
            EmployeeService employeeService,
            ScheduleValidator validator,
            CurrentUserProvider currentUser,
            AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.planningPeriodRepository = planningPeriodRepository;
        this.planningPeriodService = planningPeriodService;
        this.employeeService = employeeService;
        this.validator = validator;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    /** The three proposals for a period, with the metrics a manager compares them by. */
    @Transactional(readOnly = true)
    public List<ScheduleSummaryResponse> listProposals(UUID planningPeriodId) {
        planningPeriodService.loadInTenant(planningPeriodId);
        return scheduleRepository.findAllByPlanningPeriodIdOrderByStrategyAsc(planningPeriodId).stream()
                .map(ScheduleService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleDetailResponse get(UUID scheduleId) {
        Schedule schedule = loadInTenant(scheduleId);
        return new ScheduleDetailResponse(toSummary(schedule), shiftsWithAssignments(schedule));
    }

    /**
     * Marks one proposal as the chosen plan.
     *
     * <p>Deselecting the previous choice explicitly, in the same transaction, is what makes
     * the partial unique index ("at most one selected per period") hold. Relying on the index
     * alone would turn an ordinary click into a constraint-violation error.
     */
    @Transactional
    public ScheduleSummaryResponse select(UUID scheduleId) {
        Schedule schedule = loadInTenant(scheduleId);

        scheduleRepository
                .findByPlanningPeriodIdAndSelectedTrue(schedule.getPlanningPeriodId())
                .ifPresent(previous -> {
                    if (!previous.getId().equals(scheduleId)) {
                        previous.setSelected(false);
                        previous.setStatus(ScheduleStatus.DRAFT);
                        scheduleRepository.save(previous);
                    }
                });
        scheduleRepository.flush();

        schedule.setSelected(true);
        schedule.setStatus(ScheduleStatus.PLANNED);
        Schedule saved = scheduleRepository.save(schedule);

        auditService.record(
                AuditAction.SCHEDULE_PROPOSAL_SELECTED,
                "Schedule",
                scheduleId,
                Map.of(
                        "strategy", saved.getStrategy().name(),
                        "planningPeriodId", saved.getPlanningPeriodId().toString(),
                        "totalStaffCost", saved.getTotalStaffCost().toPlainString(),
                        "unfilledPositions", String.valueOf(saved.getUnfilledPositions())));
        return toSummary(saved);
    }

    /**
     * Reassigns one seat and re-validates the whole schedule.
     *
     * <p>Validation covers the entire schedule rather than just the edited seat, because the
     * consequences of a move are rarely local: giving Anna Saturday evening can break her rest
     * before Sunday morning and leave Saturday's kitchen a person short at the same time.
     * Reporting only the seat that moved would hide both.
     */
    @Transactional
    public ReassignResponse reassign(UUID scheduleId, UUID assignmentId, UUID newEmployeeId) {
        Schedule schedule = loadInTenant(scheduleId);
        requireEditable(schedule);

        ShiftAssignment assignment = assignmentRepository
                .findByIdAndOrganizationId(assignmentId, currentUser.requireOrganizationId())
                .filter(a -> a.getScheduleId().equals(scheduleId))
                .orElseThrow(() -> ApiException.notFound("Assignment not found in this schedule."));

        UUID previousEmployeeId = assignment.getEmployeeId();

        if (newEmployeeId != null) {
            Employee employee = employeeService.loadInTenant(newEmployeeId);
            if (!employee.isActive()) {
                throw ApiException.badRequest(
                        ErrorCode.VALIDATION_FAILED, "This employee is deactivated and cannot be scheduled.");
            }
            boolean alreadyOnThisShift = assignmentRepository
                    .findAllByScheduleIdAndShiftId(scheduleId, assignment.getShiftId())
                    .stream()
                    .anyMatch(a -> !a.getId().equals(assignmentId)
                            && newEmployeeId.equals(a.getEmployeeId()));
            if (alreadyOnThisShift) {
                throw ApiException.conflict(
                        ErrorCode.CONFLICT, "This employee already occupies another seat in this shift.");
            }
        }

        assignment.setEmployeeId(newEmployeeId);
        ShiftAssignment saved = assignmentRepository.save(assignment);
        assignmentRepository.flush();

        auditService.record(
                AuditAction.SHIFT_ASSIGNMENT_CHANGED,
                "ShiftAssignment",
                assignmentId,
                Map.of(
                        "scheduleId", scheduleId.toString(),
                        "shiftId", saved.getShiftId().toString(),
                        "before", String.valueOf(previousEmployeeId),
                        "after", String.valueOf(newEmployeeId)));

        ValidationResponse validation = validator.validate(schedule);
        return new ReassignResponse(toAssignmentResponse(saved, employeeNames()), validation);
    }

    @Transactional
    public AssignmentResponse setPinned(UUID scheduleId, UUID assignmentId, boolean pinned) {
        Schedule schedule = loadInTenant(scheduleId);
        requireEditable(schedule);

        ShiftAssignment assignment = assignmentRepository
                .findByIdAndOrganizationId(assignmentId, currentUser.requireOrganizationId())
                .filter(a -> a.getScheduleId().equals(scheduleId))
                .orElseThrow(() -> ApiException.notFound("Assignment not found in this schedule."));

        if (pinned && !assignment.isFilled()) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "An empty seat cannot be pinned. Assign someone to it first.");
        }

        assignment.setPinned(pinned);
        ShiftAssignment saved = assignmentRepository.save(assignment);

        auditService.record(
                pinned ? AuditAction.SHIFT_PINNED : AuditAction.SHIFT_UNPINNED,
                "ShiftAssignment",
                assignmentId,
                Map.of("scheduleId", scheduleId.toString(), "shiftId", saved.getShiftId().toString()));

        return toAssignmentResponse(saved, employeeNames());
    }

    @Transactional(readOnly = true)
    public ValidationResponse validate(UUID scheduleId) {
        return validator.validate(loadInTenant(scheduleId));
    }

    /**
     * Publishes the selected schedule.
     *
     * <p>This is the one place where a validation failure blocks rather than warns. During
     * editing a manager may knowingly leave a warning standing; publishing turns the plan into
     * the rota people arrange their lives around, and a hard violation there means someone is
     * rostered when they are unavailable, unqualified, or without their legal rest.
     */
    @Transactional
    public ScheduleSummaryResponse publish(UUID scheduleId) {
        Schedule schedule = loadInTenant(scheduleId);

        if (!schedule.isSelected()) {
            throw ApiException.conflict(
                    ErrorCode.CONFLICT, "Select this proposal before publishing it.");
        }

        ValidationResponse validation = validator.validate(schedule);
        if (!validation.feasible()) {
            throw new ApiException(
                    ErrorCode.SCHEDULING_CONFLICT,
                    HttpStatus.CONFLICT,
                    "This schedule still violates hard rules and cannot be published. "
                            + "Resolve the listed issues first.");
        }

        schedule.setStatus(ScheduleStatus.PUBLISHED);
        Schedule saved = scheduleRepository.save(schedule);

        List<Shift> shifts =
                shiftRepository.findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(
                        schedule.getPlanningPeriodId());
        shifts.forEach(shift -> shift.setStatus(ShiftStatus.PUBLISHED));
        shiftRepository.saveAll(shifts);

        planningPeriodRepository.findById(schedule.getPlanningPeriodId()).ifPresent(period -> {
            period.setStatus(PlanningPeriodStatus.PUBLISHED);
            planningPeriodRepository.save(period);
        });

        auditService.record(
                AuditAction.SCHEDULE_PUBLISHED,
                "Schedule",
                scheduleId,
                Map.of(
                        "planningPeriodId", saved.getPlanningPeriodId().toString(),
                        "strategy", saved.getStrategy().name(),
                        "shifts", String.valueOf(shifts.size())));

        return toSummary(saved);
    }

    /** What one employee sees: their own shifts from the published plan only. */
    @Transactional(readOnly = true)
    public MyScheduleResponse myPublishedSchedule(UUID planningPeriodId) {
        Employee employee = employeeService.requireOwnEmployeeRecord();
        PlanningPeriod period = planningPeriodService.loadInTenant(planningPeriodId);

        Schedule published = scheduleRepository
                .findByPlanningPeriodIdAndSelectedTrue(period.getId())
                .filter(s -> s.getStatus() == ScheduleStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound(
                        "No published schedule exists for this planning period yet."));

        List<ShiftWithAssignmentsResponse> all = shiftsWithAssignments(published);
        List<ShiftWithAssignmentsResponse> mine = all.stream()
                .filter(shift -> shift.assignments().stream()
                        .anyMatch(a -> employee.getId().equals(a.employeeId())))
                .toList();

        return new MyScheduleResponse(employee.getId(), mine);
    }

    @Transactional(readOnly = true)
    public Schedule loadInTenant(UUID scheduleId) {
        return scheduleRepository
                .findByIdAndOrganizationId(scheduleId, currentUser.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Schedule not found."));
    }

    private void requireEditable(Schedule schedule) {
        if (schedule.getStatus() == ScheduleStatus.PUBLISHED
                || schedule.getStatus() == ScheduleStatus.ARCHIVED) {
            throw ApiException.conflict(
                    ErrorCode.CONFLICT,
                    "A published schedule cannot be edited directly. Reopen the planning period first.");
        }
    }

    private List<ShiftWithAssignmentsResponse> shiftsWithAssignments(Schedule schedule) {
        List<Shift> shifts = shiftRepository.findAllByPlanningPeriodIdOrderByDateAscStartTimeAsc(
                schedule.getPlanningPeriodId());
        Map<UUID, List<ShiftAssignment>> assignmentsByShift = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignmentRepository.findAllByScheduleId(schedule.getId())) {
            assignmentsByShift
                    .computeIfAbsent(assignment.getShiftId(), k -> new ArrayList<>())
                    .add(assignment);
        }

        Map<UUID, String> names = employeeNames();
        Map<UUID, String> departmentNames = new LinkedHashMap<>();
        departmentRepository
                .findAllByOrganizationIdOrderByNameAsc(schedule.getOrganizationId())
                .forEach(d -> departmentNames.put(d.getId(), d.getName()));

        List<ShiftWithAssignmentsResponse> result = new ArrayList<>(shifts.size());
        for (Shift shift : shifts) {
            List<AssignmentResponse> assignments =
                    assignmentsByShift.getOrDefault(shift.getId(), List.of()).stream()
                            .sorted(java.util.Comparator.comparingInt(ShiftAssignment::getSlotIndex))
                            .map(a -> toAssignmentResponse(a, names))
                            .toList();
            result.add(new ShiftWithAssignmentsResponse(
                    shift.getId(),
                    shift.getDepartmentId(),
                    departmentNames.get(shift.getDepartmentId()),
                    shift.getDate(),
                    shift.getStartTime(),
                    shift.getEndTime(),
                    shift.isCrossesMidnight(),
                    shift.getRequiredEmployees(),
                    shift.getMinimumEmployees(),
                    assignments));
        }
        return result;
    }

    private Map<UUID, String> employeeNames() {
        Map<UUID, String> names = new LinkedHashMap<>();
        employeeRepository
                .findAllByOrganizationIdOrderByLastNameAscFirstNameAsc(currentUser.requireOrganizationId())
                .forEach(e -> names.put(e.getId(), e.fullName()));
        return names;
    }

    private static AssignmentResponse toAssignmentResponse(
            ShiftAssignment assignment, Map<UUID, String> names) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getShiftId(),
                assignment.getSlotIndex(),
                assignment.getEmployeeId(),
                assignment.getEmployeeId() == null ? null : names.get(assignment.getEmployeeId()),
                assignment.isPinned());
    }

    static ScheduleSummaryResponse toSummary(Schedule schedule) {
        return new ScheduleSummaryResponse(
                schedule.getId(),
                schedule.getPlanningPeriodId(),
                schedule.getStrategy(),
                schedule.getStatus(),
                schedule.isSelected(),
                new ScheduleMetricsResponse(
                        schedule.getTotalStaffCost(),
                        schedule.getPreferenceSatisfaction(),
                        schedule.getContractHoursDeviation(),
                        schedule.getUnfilledPositions(),
                        schedule.getOvertimeHours(),
                        schedule.getFairnessScore(),
                        schedule.getHardScore(),
                        schedule.getSoftScore(),
                        schedule.isFeasible()));
    }
}
