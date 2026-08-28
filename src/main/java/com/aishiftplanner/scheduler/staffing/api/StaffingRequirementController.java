package com.aishiftplanner.scheduler.staffing.api;

import com.aishiftplanner.scheduler.staffing.api.StaffingDtos.CreateStaffingRequirementRequest;
import com.aishiftplanner.scheduler.staffing.api.StaffingDtos.GenerateShiftsResponse;
import com.aishiftplanner.scheduler.staffing.api.StaffingDtos.StaffingRequirementResponse;
import com.aishiftplanner.scheduler.staffing.application.StaffingRequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Staffing requirements", description = "How many people, when, where and with which skills")
public class StaffingRequirementController {

    private final StaffingRequirementService staffingRequirementService;

    public StaffingRequirementController(StaffingRequirementService staffingRequirementService) {
        this.staffingRequirementService = staffingRequirementService;
    }

    @GetMapping("/planning-periods/{planningPeriodId}/staffing-requirements")
    @Operation(summary = "List the staffing requirements of a planning period")
    public List<StaffingRequirementResponse> list(@PathVariable UUID planningPeriodId) {
        return staffingRequirementService.listForPeriod(planningPeriodId);
    }

    @PostMapping("/planning-periods/{planningPeriodId}/staffing-requirements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Define a staffing requirement")
    public StaffingRequirementResponse create(
            @PathVariable UUID planningPeriodId,
            @Valid @RequestBody CreateStaffingRequirementRequest request) {
        return staffingRequirementService.create(planningPeriodId, request);
    }

    @DeleteMapping("/staffing-requirements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a staffing requirement")
    public void delete(@PathVariable UUID id) {
        staffingRequirementService.delete(id);
    }

    @PostMapping("/planning-periods/{planningPeriodId}/shifts/generate")
    @Operation(summary = "Turn the period's staffing requirements into concrete shifts (replaces existing)")
    public GenerateShiftsResponse generateShifts(@PathVariable UUID planningPeriodId) {
        return staffingRequirementService.generateShifts(planningPeriodId);
    }
}
