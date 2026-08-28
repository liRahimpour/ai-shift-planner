package com.aishiftplanner.scheduler.employee.application;

import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.CreateEmployeeRequest;
import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.EmployeeResponse;
import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.UpdateEmployeeRequest;
import com.aishiftplanner.scheduler.employee.domain.Employee;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import com.aishiftplanner.scheduler.organization.application.LocationService;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final LocationService locationService;
    private final SkillService skillService;
    private final CurrentUserProvider currentUser;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            LocationService locationService,
            SkillService skillService,
            CurrentUserProvider currentUser) {
        this.employeeRepository = employeeRepository;
        this.locationService = locationService;
        this.skillService = skillService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(UUID locationId) {
        AuthenticatedUser caller = currentUser.require();
        List<Employee> employees = locationId == null
                ? employeeRepository.findAllByOrganizationIdOrderByLastNameAscFirstNameAsc(
                        caller.organizationId())
                : employeeRepository.findAllByOrganizationIdAndLocationIdOrderByLastNameAscFirstNameAsc(
                        caller.organizationId(), requireLocationInTenant(locationId));
        return employees.stream().map(e -> toResponse(e, caller)).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID id) {
        return toResponse(loadInTenant(id), currentUser.require());
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        UUID organizationId = currentUser.requireOrganizationId();
        requireLocationInTenant(request.locationId());
        skillService.requireAllExistInTenant(request.skillIds());
        validateHours(request.minimumHoursPerWeek(), request.maximumHoursPerWeek());

        Employee employee =
                new Employee(organizationId, request.locationId(), request.firstName(), request.lastName());
        applyEditableFields(
                employee,
                request.email(),
                request.employmentType(),
                request.hourlyWage(),
                request.contractHoursPerWeek(),
                request.minimumHoursPerWeek(),
                request.maximumHoursPerWeek(),
                request.skillIds(),
                request.departmentIds(),
                request.additionalLocationIds());

        return toResponse(employeeRepository.save(employee), currentUser.require());
    }

    @Transactional
    public EmployeeResponse update(UUID id, UpdateEmployeeRequest request) {
        requireLocationInTenant(request.locationId());
        skillService.requireAllExistInTenant(request.skillIds());
        validateHours(request.minimumHoursPerWeek(), request.maximumHoursPerWeek());

        Employee employee = loadInTenant(id);
        employee.setLocationId(request.locationId());
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setActive(request.active());
        applyEditableFields(
                employee,
                request.email(),
                request.employmentType(),
                request.hourlyWage(),
                request.contractHoursPerWeek(),
                request.minimumHoursPerWeek(),
                request.maximumHoursPerWeek(),
                request.skillIds(),
                request.departmentIds(),
                request.additionalLocationIds());

        return toResponse(employeeRepository.save(employee), currentUser.require());
    }

    @Transactional(readOnly = true)
    public Employee loadInTenant(UUID id) {
        return employeeRepository
                .findByIdAndOrganizationId(id, currentUser.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Employee not found."));
    }

    /** @return the employee record linked to the calling user, if they have one. */
    @Transactional(readOnly = true)
    public Employee requireOwnEmployeeRecord() {
        AuthenticatedUser caller = currentUser.require();
        return employeeRepository
                .findByUserId(caller.userId())
                .filter(e -> e.getOrganizationId().equals(caller.organizationId()))
                .orElseThrow(() -> ApiException.notFound("No employee record is linked to your account."));
    }

    private void applyEditableFields(
            Employee employee,
            String email,
            com.aishiftplanner.scheduler.employee.domain.EmploymentType employmentType,
            BigDecimal hourlyWage,
            BigDecimal contractHours,
            BigDecimal minimumHours,
            BigDecimal maximumHours,
            Set<UUID> skillIds,
            Set<UUID> departmentIds,
            Set<UUID> additionalLocationIds) {
        employee.setEmail(email);
        employee.setEmploymentType(employmentType);
        employee.setHourlyWage(hourlyWage);
        employee.setContractHoursPerWeek(contractHours);
        employee.setMinimumHoursPerWeek(minimumHours);
        employee.setMaximumHoursPerWeek(maximumHours);
        employee.setSkillIds(skillIds == null ? Set.of() : skillIds);
        employee.setDepartmentIds(departmentIds == null ? Set.of() : departmentIds);
        employee.setAdditionalLocationIds(additionalLocationIds == null ? Set.of() : additionalLocationIds);
    }

    private UUID requireLocationInTenant(UUID locationId) {
        locationService.loadInTenant(locationId);
        return locationId;
    }

    private void validateHours(BigDecimal minimum, BigDecimal maximum) {
        if (minimum.compareTo(maximum) > 0) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED,
                    "minimumHoursPerWeek must not be greater than maximumHoursPerWeek.");
        }
    }

    /**
     * Maps to the response DTO, redacting the hourly wage for callers who are not managers.
     *
     * <p>An employee can legitimately list their colleagues (to find a swap partner, for
     * instance), but pay is not theirs to see. Doing the redaction here rather than in the
     * UI is the difference between a privacy control and a cosmetic one — the same rule then
     * automatically covers the AI chat, which reads employees through this service.
     */
    EmployeeResponse toResponse(Employee employee, AuthenticatedUser caller) {
        boolean maySeeWage = caller.isManager() || employee.getUserId() != null
                && employee.getUserId().equals(caller.userId());
        return new EmployeeResponse(
                employee.getId(),
                employee.getOrganizationId(),
                employee.getLocationId(),
                employee.getUserId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getEmploymentType(),
                maySeeWage ? employee.getHourlyWage() : null,
                employee.getContractHoursPerWeek(),
                employee.getMinimumHoursPerWeek(),
                employee.getMaximumHoursPerWeek(),
                employee.getSkillIds(),
                employee.getDepartmentIds(),
                employee.getAdditionalLocationIds(),
                employee.isActive());
    }
}
