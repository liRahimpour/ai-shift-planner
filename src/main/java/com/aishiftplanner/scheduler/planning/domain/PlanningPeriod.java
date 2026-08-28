package com.aishiftplanner.scheduler.planning.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The unit everything else hangs off: employees submit availability for a period, managers
 * define staffing requirements for it, and the solver produces schedules for it.
 *
 * <p>Dates are {@link LocalDate} in the location's own timezone; the deadline is an
 * {@link Instant} because "Wednesday at 18:00" only becomes a real moment once anchored to a
 * zone, and the comparison against "now" has to be unambiguous across a DST boundary.
 */
@Entity
@Table(name = "planning_periods")
public class PlanningPeriod extends TenantScopedEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "availability_deadline", nullable = false)
    private Instant availabilityDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlanningPeriodStatus status = PlanningPeriodStatus.OPEN_FOR_AVAILABILITY;

    @Column(name = "created_by")
    private UUID createdBy;

    protected PlanningPeriod() {
        // for JPA
    }

    public PlanningPeriod(
            UUID organizationId,
            UUID locationId,
            LocalDate startDate,
            LocalDate endDate,
            Instant availabilityDeadline,
            UUID createdBy) {
        setOrganizationId(organizationId);
        this.locationId = locationId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.availabilityDeadline = availabilityDeadline;
        this.createdBy = createdBy;
    }

    /**
     * Whether employees may still change their availability.
     *
     * <p>Two independent gates, both of which must be open: the period's status, and the
     * deadline itself. Checking only the clock would let a manager's "reopen" be ignored;
     * checking only the status would let submissions trickle in after the deadline until
     * someone manually flipped the status.
     */
    public boolean acceptsAvailabilityAt(Instant now) {
        return status.allowsAvailabilityEditing() && now.isBefore(availabilityDeadline);
    }

    public boolean deadlineHasPassed(Instant now) {
        return !now.isBefore(availabilityDeadline);
    }

    /** Reopens the availability window with a new deadline (manager action, always audited). */
    public void reopenAvailability(Instant newDeadline) {
        this.availabilityDeadline = newDeadline;
        this.status = PlanningPeriodStatus.OPEN_FOR_AVAILABILITY;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Instant getAvailabilityDeadline() {
        return availabilityDeadline;
    }

    public void setAvailabilityDeadline(Instant availabilityDeadline) {
        this.availabilityDeadline = availabilityDeadline;
    }

    public PlanningPeriodStatus getStatus() {
        return status;
    }

    public void setStatus(PlanningPeriodStatus status) {
        this.status = status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
