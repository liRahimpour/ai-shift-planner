package com.aishiftplanner.scheduler.availability.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One availability window an employee declared for one day of a planning period.
 *
 * <p>Several rows may share the same (employee, date) so that a split day such as
 * "10:00–14:00 and 18:00–23:00" is expressible. A null start/end means the type applies to
 * the whole day, which is the common shape for {@code UNAVAILABLE}.
 */
@Entity
@Table(name = "availabilities")
public class Availability extends TenantScopedEntity {

    @Column(name = "planning_period_id", nullable = false)
    private UUID planningPeriodId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_type", nullable = false, length = 20)
    private AvailabilityType availabilityType;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    protected Availability() {
        // for JPA
    }

    public Availability(
            UUID organizationId,
            UUID planningPeriodId,
            UUID employeeId,
            LocalDate date,
            AvailabilityType availabilityType,
            LocalTime startTime,
            LocalTime endTime) {
        setOrganizationId(organizationId);
        this.planningPeriodId = planningPeriodId;
        this.employeeId = employeeId;
        this.date = date;
        this.availabilityType = availabilityType;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean isWholeDay() {
        return startTime == null || endTime == null;
    }

    /**
     * True if this window fully contains {@code [shiftStart, shiftEnd)} on the same day.
     *
     * <p>Full containment, not overlap, is the right test: an employee who is available
     * 08:00–16:00 must not be assigned a 14:00–22:00 shift just because the two intervals
     * touch. Overnight shifts are handled by the caller, which splits them per calendar day
     * before asking.
     */
    public boolean covers(LocalTime shiftStart, LocalTime shiftEnd) {
        if (isWholeDay()) {
            return true;
        }
        return !shiftStart.isBefore(startTime) && !shiftEnd.isAfter(endTime);
    }

    /** True if this window overlaps {@code [shiftStart, shiftEnd)} at all. */
    public boolean overlaps(LocalTime shiftStart, LocalTime shiftEnd) {
        if (isWholeDay()) {
            return true;
        }
        return shiftStart.isBefore(endTime) && shiftEnd.isAfter(startTime);
    }

    public UUID getPlanningPeriodId() {
        return planningPeriodId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public AvailabilityType getAvailabilityType() {
        return availabilityType;
    }

    public void setAvailabilityType(AvailabilityType availabilityType) {
        this.availabilityType = availabilityType;
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
}
