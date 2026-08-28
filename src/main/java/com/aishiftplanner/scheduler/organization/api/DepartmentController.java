package com.aishiftplanner.scheduler.organization.api;

import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.DepartmentResponse;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.UpdateDepartmentRequest;
import com.aishiftplanner.scheduler.organization.application.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "Departments", description = "Work areas within a location (Küche, Theke, Bar, …)")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @Operation(summary = "List every department in the caller's organization")
    public List<DepartmentResponse> list() {
        return departmentService.listForOrganization();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'LOCATION_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Update a department")
    public DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        return departmentService.update(id, request);
    }
}
