package com.aishiftplanner.scheduler.employee.api;

import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.CreateEmployeeRequest;
import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.EmployeeResponse;
import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.UpdateEmployeeRequest;
import com.aishiftplanner.scheduler.employee.application.EmployeeService;
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
@RequestMapping("/api/v1/employees")
@Tag(name = "Employees", description = "Schedulable staff, their skills, contracts and hour limits")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    @Operation(summary = "List employees, optionally filtered by location")
    public List<EmployeeResponse> list(@RequestParam(required = false) UUID locationId) {
        return employeeService.list(locationId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one employee")
    public EmployeeResponse get(@PathVariable UUID id) {
        return employeeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'LOCATION_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Create an employee")
    public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest request) {
        return employeeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'LOCATION_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Update an employee")
    public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(id, request);
    }
}
