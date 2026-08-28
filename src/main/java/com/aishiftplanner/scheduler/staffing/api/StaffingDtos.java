package com.aishiftplanner.scheduler.staffing.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

public final class StaffingDtos {

    private StaffingDtos() {
    }

    /**
     * @param crossesMidnight true for blocks like "Bar 18:00–02:00"; without it, an end time
     *     earlier than the start time is indistinguishable from a typo
     * @param requiredSkills skill id → how many of the assigned people must hold it
     */
    public record CreateStaffingRequirementRequest(
            @NotNull UUID departmentId,
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            boolean crossesMidnight,
            @Min(0) int minimumStaff,
            @Min(0) int preferredStaff,
            @Min(0) int maximumStaff,
            Map<UUID, Integer> requiredSkills) {
    }

    public record StaffingRequirementResponse(
            UUID id,
            UUID organizationId,
            UUID locationId,
            UUID departmentId,
            UUID planningPeriodId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            boolean crossesMidnight,
            int minimumStaff,
            int preferredStaff,
            int maximumStaff,
            double durationHours,
            Map<UUID, Integer> requiredSkills) {
    }

    /** Result of turning a period's requirements into concrete shifts. */
    public record GenerateShiftsResponse(int requirementsProcessed, int shiftsCreated) {
    }
}
