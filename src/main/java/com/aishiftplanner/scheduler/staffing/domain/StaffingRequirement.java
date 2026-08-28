package com.aishiftplanner.scheduler.staffing.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
 * What a manager says the floor needs — e.g. "Saturday, Küche, 16:00–23:00, 4 people".
 *
 * <p>Requirements and shifts are separate entities. The requirement is the record of intent
 * and survives re-planning; a {@code Shift} is the concrete block that gets staffed and can
 * be split or moved by hand afterwards without rewriting what the manager originally asked
 * for.
 */
@Entity
@Table(name = "staffing_requirements")
public class StaffingRequirement extends TenantScopedEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "planning_period_id", nullable = false)
    private UUID planningPeriodId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "crosses_midnight", nullable = false)
    private boolean crossesMidnight;

    @Column(name = "minimum_staff", nullable = false)
    private int minimumStaff;

    @Column(name = "preferred_staff", nullable = false)
    private int preferredStaff;

    @Column(name = "maximum_staff", nullable = false)
    private int maximumStaff;

    /**
     * How many people holding each skill this block needs — "at least 1× BAR and 1× CLOSING"
     * is two entries here. A plain set of skills could not express "two people with BAR".
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "staffing_requirement_skills",
            joinColumns = @JoinColumn(name = "requirement_id"))
    @MapKeyColumn(name = "skill_id")
    @Column(name = "required_count", nullable = false)
    private Map<UUID, Integer> requiredSkills = new HashMap<>();

    protected StaffingRequirement() {
        // for JPA
    }

    public StaffingRequirement(
            UUID organizationId,
            UUID locationId,
            UUID departmentId,
            UUID planningPeriodId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            boolean crossesMidnight) {
        setOrganizationId(organizationId);
        this.locationId = locationId;
        this.departmentId = departmentId;
        this.planningPeriodId = planningPeriodId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.crossesMidnight = crossesMidnight;
    }

    public TimeWindow window() {
        return TimeWindow.of(date, startTime, endTime, crossesMidnight);
    }

    public UUID getLocationId() {
        return locationId;
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

    public int getMinimumStaff() {
        return minimumStaff;
    }

    public void setMinimumStaff(int minimumStaff) {
        this.minimumStaff = minimumStaff;
    }

    public int getPreferredStaff() {
        return preferredStaff;
    }

    public void setPreferredStaff(int preferredStaff) {
        this.preferredStaff = preferredStaff;
    }

    public int getMaximumStaff() {
        return maximumStaff;
    }

    public void setMaximumStaff(int maximumStaff) {
        this.maximumStaff = maximumStaff;
    }

    public Map<UUID, Integer> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(Map<UUID, Integer> requiredSkills) {
        this.requiredSkills = new HashMap<>(requiredSkills);
    }
}
