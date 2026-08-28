package com.aishiftplanner.scheduler.planning.api;

import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.AvailabilityResponse;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.CommentResponse;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.SubmitAvailabilityRequest;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.SubmitCommentRequest;
import com.aishiftplanner.scheduler.availability.application.AvailabilityService;
import com.aishiftplanner.scheduler.availability.application.EmployeeCommentService;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.ChangeDeadlineRequest;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.ChangeStatusRequest;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.CreatePlanningPeriodRequest;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.PlanningPeriodResponse;
import com.aishiftplanner.scheduler.planning.api.PlanningDtos.PlanningPeriodSummaryResponse;
import com.aishiftplanner.scheduler.planning.application.PlanningPeriodService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planning-periods")
@Tag(name = "Planning periods", description = "Scheduling windows, availability deadlines and submissions")
public class PlanningPeriodController {

    private final PlanningPeriodService planningPeriodService;
    private final AvailabilityService availabilityService;
    private final EmployeeCommentService commentService;

    public PlanningPeriodController(
            PlanningPeriodService planningPeriodService,
            AvailabilityService availabilityService,
            EmployeeCommentService commentService) {
        this.planningPeriodService = planningPeriodService;
        this.availabilityService = availabilityService;
        this.commentService = commentService;
    }

    @GetMapping
    @Operation(summary = "List planning periods for a location")
    public List<PlanningPeriodResponse> list(@RequestParam UUID locationId) {
        return planningPeriodService.listForLocation(locationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create a planning period")
    public PlanningPeriodResponse create(@Valid @RequestBody CreatePlanningPeriodRequest request) {
        return planningPeriodService.create(request);
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Dashboard numbers: submissions received, missing, comments, pending reviews")
    public PlanningPeriodSummaryResponse summary(@PathVariable UUID id) {
        return planningPeriodService.summary(id);
    }

    @PutMapping("/{id}/deadline")
    @PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Change or reopen the availability deadline (audited)")
    public PlanningPeriodResponse changeDeadline(
            @PathVariable UUID id, @Valid @RequestBody ChangeDeadlineRequest request) {
        return planningPeriodService.changeDeadline(id, request);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Move the planning period through its lifecycle")
    public PlanningPeriodResponse changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return planningPeriodService.changeStatus(id, request.status());
    }

    // --- Availability --------------------------------------------------------

    @GetMapping("/{id}/availability/me")
    @Operation(summary = "The calling employee's own submission")
    public List<AvailabilityResponse> myAvailability(@PathVariable UUID id) {
        return availabilityService.listOwn(id);
    }

    @PutMapping("/{id}/availability/me")
    @Operation(summary = "Replace the calling employee's submission (blocked after the deadline)")
    public List<AvailabilityResponse> submitMyAvailability(
            @PathVariable UUID id, @Valid @RequestBody SubmitAvailabilityRequest request) {
        return availabilityService.submitOwn(id, request);
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "All submissions for a period (managers only)")
    public List<AvailabilityResponse> allAvailability(@PathVariable UUID id) {
        return availabilityService.listForPeriod(id);
    }

    @PutMapping("/{id}/availability/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Record availability on an employee's behalf (bypasses the deadline; audited)")
    public List<AvailabilityResponse> submitForEmployee(
            @PathVariable UUID id,
            @PathVariable UUID employeeId,
            @Valid @RequestBody SubmitAvailabilityRequest request) {
        return availabilityService.submitForEmployee(id, employeeId, request);
    }

    // --- Comments ------------------------------------------------------------

    @PostMapping("/{id}/comments/me")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a free-text comment to the calling employee's submission")
    public CommentResponse submitMyComment(
            @PathVariable UUID id, @Valid @RequestBody SubmitCommentRequest request) {
        return commentService.submitOwn(id, request);
    }

    @GetMapping("/{id}/comments/me")
    @Operation(summary = "The calling employee's own comments")
    public List<CommentResponse> myComments(@PathVariable UUID id) {
        return commentService.listOwn(id);
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "All comments for a period, with their AI interpretations")
    public List<CommentResponse> comments(@PathVariable UUID id) {
        return commentService.listForPeriod(id);
    }
}
