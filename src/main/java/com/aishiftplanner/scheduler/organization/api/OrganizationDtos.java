package com.aishiftplanner.scheduler.organization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request/response payloads for the organization module.
 *
 * <p>JPA entities are never exposed directly (product brief §36): a DTO boundary keeps the
 * API contract stable when the schema changes, prevents lazy-loading surprises during
 * serialization, and — most importantly here — makes it impossible to accidentally leak a
 * field like a password hash or another tenant's id by adding a column.
 */
public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    // --- Organization --------------------------------------------------------
    public record OrganizationResponse(UUID id, String name, String slug, boolean active) {
    }

    // --- Location ------------------------------------------------------------
    public record CreateLocationRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 64) String timezone,
            @Size(max = 255) String addressLine,
            @Size(max = 20) String postalCode,
            @Size(max = 120) String city,
            @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two-letter ISO country code")
                    String countryCode) {
    }

    public record UpdateLocationRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 64) String timezone,
            @Size(max = 255) String addressLine,
            @Size(max = 20) String postalCode,
            @Size(max = 120) String city,
            @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two-letter ISO country code")
                    String countryCode,
            boolean active) {
    }

    public record LocationResponse(
            UUID id,
            UUID organizationId,
            String name,
            String timezone,
            String addressLine,
            String postalCode,
            String city,
            String countryCode,
            boolean active) {
    }

    // --- Department ----------------------------------------------------------
    public record CreateDepartmentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {
    }

    public record UpdateDepartmentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            boolean active) {
    }

    public record DepartmentResponse(
            UUID id, UUID organizationId, UUID locationId, String name, String description, boolean active) {
    }
}
