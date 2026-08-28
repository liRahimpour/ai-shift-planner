package com.aishiftplanner.scheduler.employee.api;

import com.aishiftplanner.scheduler.employee.domain.EmploymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    // --- Skills --------------------------------------------------------------
    public record CreateSkillRequest(
            @NotBlank
                    @Pattern(
                            regexp = "^[A-Z][A-Z0-9_]{1,59}$",
                            message = "must be uppercase A-Z, digits and underscores, e.g. COFFEE_MACHINE")
                    String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {
    }

    public record SkillResponse(
            UUID id, UUID organizationId, String code, String name, String description, boolean active) {
    }

    // --- Employees -----------------------------------------------------------
    public record CreateEmployeeRequest(
            @NotNull UUID locationId,
            @NotBlank @Size(max = 120) String firstName,
            @NotBlank @Size(max = 120) String lastName,
            @Email @Size(max = 255) String email,
            @NotNull EmploymentType employmentType,
            @NotNull @DecimalMin("0.00") BigDecimal hourlyWage,
            @NotNull @DecimalMin("0.00") BigDecimal contractHoursPerWeek,
            @NotNull @DecimalMin("0.00") BigDecimal minimumHoursPerWeek,
            @NotNull @DecimalMin("0.00") BigDecimal maximumHoursPerWeek,
            Set<UUID> skillIds,
            Set<UUID> departmentIds,
            Set<UUID> additionalLocationIds) {
    }

    public record UpdateEmployeeRequest(
            @NotNull UUID locationId,
            @NotBlank @Size(max = 120) String firstName,
            @NotBlank @Size(max = 120) String lastName,
            @Email @Size(max = 255) String email,
            @NotNull EmploymentType employmentType,
            @NotNull @DecimalMin("0.00") BigDecimal hourlyWage,
            @NotNull @DecimalMin("0.00") BigDecimal contractHoursPerWeek,
            @NotNull @DecimalMin("0.00") BigDecimal minimumHoursPerWeek,
            @NotNull @DecimalMin("0.00") BigDecimal maximumHoursPerWeek,
            Set<UUID> skillIds,
            Set<UUID> departmentIds,
            Set<UUID> additionalLocationIds,
            boolean active) {
    }

    /**
     * Wage is included only for callers who are allowed to see it — see
     * {@code EmployeeService.toResponse}, which nulls it out for non-managers rather than
     * relying on the frontend to hide the column.
     */
    public record EmployeeResponse(
            UUID id,
            UUID organizationId,
            UUID locationId,
            UUID userId,
            String firstName,
            String lastName,
            String email,
            EmploymentType employmentType,
            BigDecimal hourlyWage,
            BigDecimal contractHoursPerWeek,
            BigDecimal minimumHoursPerWeek,
            BigDecimal maximumHoursPerWeek,
            Set<UUID> skillIds,
            Set<UUID> departmentIds,
            Set<UUID> additionalLocationIds,
            boolean active) {
    }
}
