package com.aishiftplanner.scheduler.schedule.api;

import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.AssignmentResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.MyScheduleResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.PinRequest;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.PlanningJobResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ReassignRequest;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ReassignResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ScheduleDetailResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ScheduleSummaryResponse;
import com.aishiftplanner.scheduler.schedule.api.ScheduleDtos.ValidationResponse;
import com.aishiftplanner.scheduler.schedule.application.PlanningJobService;
import com.aishiftplanner.scheduler.schedule.application.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Schedules", description = "Generating, comparing, editing and publishing schedules")
public class ScheduleController {

    private static final String MANAGER_ROLES =
            "hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')";

    private final ScheduleService scheduleService;
    private final PlanningJobService planningJobService;

    public ScheduleController(ScheduleService scheduleService, PlanningJobService planningJobService) {
        this.scheduleService = scheduleService;
        this.planningJobService = planningJobService;
    }

    // --- Generation ----------------------------------------------------------

    @PostMapping("/planning-periods/{planningPeriodId}/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(MANAGER_ROLES)
    @Operation(
            summary = "Queue a planning run producing the FAIR, COST_OPTIMIZED and BALANCED proposals",
            description = "Returns immediately with a job id. Clicking twice returns the same job "
                    + "rather than starting a second run.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Queued (or already running)"),
        @ApiResponse(responseCode = "409", description = "The planning period is not ready for planning")
    })
    public PlanningJobResponse generate(@PathVariable UUID planningPeriodId) {
        return planningJobService.requestPlanning(planningPeriodId);
    }

    @GetMapping("/planning-jobs/{jobId}")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(summary = "Poll the status of a planning run")
    public PlanningJobResponse jobStatus(@PathVariable UUID jobId) {
        return planningJobService.get(jobId);
    }

    @GetMapping("/planning-periods/{planningPeriodId}/planning-jobs")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(summary = "List planning runs for a period, newest first")
    public List<PlanningJobResponse> jobs(@PathVariable UUID planningPeriodId) {
        return planningJobService.listForPeriod(planningPeriodId);
    }

    @PostMapping("/planning-jobs/{jobId}/cancel")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(summary = "Cancel a queued or running planning job")
    public PlanningJobResponse cancelJob(@PathVariable UUID jobId) {
        return planningJobService.cancel(jobId);
    }

    // --- Proposals -----------------------------------------------------------

    @GetMapping("/planning-periods/{planningPeriodId}/schedule-proposals")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(summary = "The proposals for a period with their comparison metrics")
    public List<ScheduleSummaryResponse> proposals(@PathVariable UUID planningPeriodId) {
        return scheduleService.listProposals(planningPeriodId);
    }

    @GetMapping("/schedules/{scheduleId}")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(summary = "One schedule with all its shifts and assignments")
    public ScheduleDetailResponse get(@PathVariable UUID scheduleId) {
        return scheduleService.get(scheduleId);
    }

    @PostMapping("/schedules/{scheduleId}/select")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(summary = "Choose this proposal as the plan to work from")
    public ScheduleSummaryResponse select(@PathVariable UUID scheduleId) {
        return scheduleService.select(scheduleId);
    }

    // --- Manual editing ------------------------------------------------------

    @PutMapping("/schedules/{scheduleId}/assignments/{assignmentId}")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(
            summary = "Reassign one seat (null clears it) and re-validate the whole schedule",
            description = "Returns the updated assignment together with every issue the change "
                    + "introduced anywhere in the schedule, including warnings such as "
                    + "insufficient rest before a later shift.")
    public ReassignResponse reassign(
            @PathVariable UUID scheduleId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody ReassignRequest request) {
        return scheduleService.reassign(scheduleId, assignmentId, request.employeeId());
    }

    @PutMapping("/schedules/{scheduleId}/assignments/{assignmentId}/pin")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(summary = "Pin or unpin an assignment so re-optimization leaves it untouched")
    public AssignmentResponse pin(
            @PathVariable UUID scheduleId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody PinRequest request) {
        return scheduleService.setPinned(scheduleId, assignmentId, request.pinned());
    }

    @GetMapping("/schedules/{scheduleId}/validation")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(summary = "Re-check a schedule against the hard rules and report warnings")
    public ValidationResponse validate(@PathVariable UUID scheduleId) {
        return scheduleService.validate(scheduleId);
    }

    // --- Publishing ----------------------------------------------------------

    @PostMapping("/schedules/{scheduleId}/publish")
    @PreAuthorize(MANAGER_ROLES)
    @Operation(
            summary = "Publish the selected schedule to employees",
            description = "Refused while any hard rule is still violated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Published"),
        @ApiResponse(responseCode = "409", description = "Hard rules are still violated, or not selected")
    })
    public ScheduleSummaryResponse publish(@PathVariable UUID scheduleId) {
        return scheduleService.publish(scheduleId);
    }

    // --- Employee view -------------------------------------------------------

    @GetMapping("/planning-periods/{planningPeriodId}/my-schedule")
    @Operation(summary = "The calling employee's own shifts from the published plan")
    public MyScheduleResponse mySchedule(@PathVariable UUID planningPeriodId) {
        return scheduleService.myPublishedSchedule(planningPeriodId);
    }
}
