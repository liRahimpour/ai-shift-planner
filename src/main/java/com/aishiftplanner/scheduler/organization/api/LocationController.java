package com.aishiftplanner.scheduler.organization.api;

import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.CreateDepartmentRequest;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.CreateLocationRequest;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.DepartmentResponse;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.LocationResponse;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.UpdateLocationRequest;
import com.aishiftplanner.scheduler.organization.application.DepartmentService;
import com.aishiftplanner.scheduler.organization.application.LocationService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Locations", description = "Sites belonging to an organization, and their departments")
public class LocationController {

    private final LocationService locationService;
    private final DepartmentService departmentService;

    public LocationController(LocationService locationService, DepartmentService departmentService) {
        this.locationService = locationService;
        this.departmentService = departmentService;
    }

    @GetMapping
    @Operation(summary = "List all locations of the caller's organization")
    public List<LocationResponse> list() {
        return locationService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one location")
    public LocationResponse get(@PathVariable UUID id) {
        return locationService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create a location (org admins only)")
    public LocationResponse create(@Valid @RequestBody CreateLocationRequest request) {
        return locationService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'LOCATION_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Update a location")
    public LocationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateLocationRequest request) {
        return locationService.update(id, request);
    }

    @GetMapping("/{locationId}/departments")
    @Operation(summary = "List the departments of a location")
    public List<DepartmentResponse> departments(@PathVariable UUID locationId) {
        return departmentService.listForLocation(locationId);
    }

    @PostMapping("/{locationId}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'LOCATION_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Create a department at a location")
    public DepartmentResponse createDepartment(
            @PathVariable UUID locationId, @Valid @RequestBody CreateDepartmentRequest request) {
        return departmentService.create(locationId, request);
    }
}
