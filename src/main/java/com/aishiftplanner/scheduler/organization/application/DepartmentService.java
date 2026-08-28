package com.aishiftplanner.scheduler.organization.application;

import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.CreateDepartmentRequest;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.DepartmentResponse;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.UpdateDepartmentRequest;
import com.aishiftplanner.scheduler.organization.domain.Department;
import com.aishiftplanner.scheduler.organization.infrastructure.DepartmentRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final LocationService locationService;
    private final CurrentUserProvider currentUser;

    public DepartmentService(
            DepartmentRepository departmentRepository,
            LocationService locationService,
            CurrentUserProvider currentUser) {
        this.departmentRepository = departmentRepository;
        this.locationService = locationService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listForLocation(UUID locationId) {
        // Proves the location belongs to the caller's tenant before listing anything under it.
        locationService.loadInTenant(locationId);
        return departmentRepository
                .findAllByLocationIdAndOrganizationIdOrderByNameAsc(
                        locationId, currentUser.requireOrganizationId())
                .stream()
                .map(DepartmentService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listForOrganization() {
        return departmentRepository
                .findAllByOrganizationIdOrderByNameAsc(currentUser.requireOrganizationId())
                .stream()
                .map(DepartmentService::toResponse)
                .toList();
    }

    @Transactional
    public DepartmentResponse create(UUID locationId, CreateDepartmentRequest request) {
        locationService.loadInTenant(locationId);

        if (departmentRepository.existsByLocationIdAndName(locationId, request.name())) {
            throw ApiException.conflict(
                    ErrorCode.ALREADY_EXISTS, "A department with this name already exists at this location.");
        }

        Department department =
                new Department(currentUser.requireOrganizationId(), locationId, request.name());
        department.setDescription(request.description());
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse update(UUID id, UpdateDepartmentRequest request) {
        Department department = loadInTenant(id);
        department.setName(request.name());
        department.setDescription(request.description());
        department.setActive(request.active());
        return toResponse(departmentRepository.save(department));
    }

    @Transactional(readOnly = true)
    public Department loadInTenant(UUID id) {
        return departmentRepository
                .findByIdAndOrganizationId(id, currentUser.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Department not found."));
    }

    static DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getOrganizationId(),
                department.getLocationId(),
                department.getName(),
                department.getDescription(),
                department.isActive());
    }
}
