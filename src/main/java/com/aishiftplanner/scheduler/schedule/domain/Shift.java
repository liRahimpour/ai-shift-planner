package com.aishiftplanner.scheduler.schedule.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A concrete block of work to be staffed.
 *
 * <p>Generated from a {@link com.aishiftplanner.scheduler.staffing.domain.StaffingRequirement}
 * but editable independently afterwards, so a manager can split or move one shift without
 * rewriting the requirement it came from.
 */
@Entity
@Table(name = "shifts")
public class Shift extends TenantScopedEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "planning_period_id", nullable = false)
    private UUID planningPeriodId;

    /** Null for shifts created by hand rather than generated from a requirement. */
    @Column(name = "requirement_id")
    private UUID requirementId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "crosses_midnight", nullable = false)
    private boolean crossesMidnight;

    @Column(name = "required_employees", nullable = false)
    private int requiredEmployees;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShiftStatus status = ShiftStatus.DRAFT;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "shift_required_skills", joinColumns = @JoinColumn(name = "shift_id"))
    @MapKeyColumn(name = "skill_id")
    @Column(name = "required_count", nullable = false)
    private Map<UUID, Integer> requiredSkills = new HashMap<>();

    protected Shift() {
        // for JPA
    }

    public Shift(
            UUID organizationId,
            UUID locationId,
            UUID departmentId,
            UUID planningPeriodId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            boolean crossesMidnight,
            int requiredEmployees) {
        setOrganizationId(organizationId);
        this.locationId = locationId;
        this.departmentId = departmentId;
        this.planningPeriodId = planningPeriodId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.crossesMidnight = crossesMidnight;
        this.requiredEmployees = requiredEmployees;
    }

    /** Resolved local start/end, with midnight-crossing already handled. */
    public TimeWindow window() {
        return TimeWindow.of(date, startTime, endTime, crossesMidnight);
    }

    public double durationHours() {
        return window().hours();
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getPlanningPeriodId() {
        return planningPeriodId;
    }

    public UUID getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(UUID requirementId) {
        this.requirementId = requirementId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public boolean isCrossesMidnight() {
        return crossesMidnight;
    }

    public void setCrossesMidnight(boolean crossesMidnight) {
        this.crossesMidnight = crossesMidnight;
    }

    public int getRequiredEmployees() {
        return requiredEmployees;
    }

    public void setRequiredEmployees(int requiredEmployees) {
        this.requiredEmployees = requiredEmployees;
    }

    public ShiftStatus getStatus() {
        return status;
    }

    public void setStatus(ShiftStatus status) {
        this.status = status;
    }

    public Map<UUID, Integer> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(Map<UUID, Integer> requiredSkills) {
        this.requiredSkills = new HashMap<>(requiredSkills);
    }
}
