package com.aishiftplanner.scheduler.schedule.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One seat in one shift of one schedule: "the second of four kitchen slots on Saturday".
 *
 * <p>A row exists for every seat, whether or not it is filled. An unfilled seat is data the
 * manager must see — "two positions still open" is the single most actionable number on the
 * dashboard — so it is represented by a row with a null {@code employeeId}, never by the
 * absence of a row.
 *
 * <p>This is also the JPA-side twin of the Timefold planning entity: the solver works on
 * lightweight in-memory objects, and the result is written back here.
 */
@Entity
@Table(name = "shift_assignments")
public class ShiftAssignment extends TenantScopedEntity {

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    /** Null means this seat is unfilled. */
    @Column(name = "employee_id")
    private UUID employeeId;

    /**
     * A manager's explicit decision that survives re-optimization untouched.
     *
     * <p>Without pinning, a manager who fixes one assignment by hand and then reruns the
     * solver would silently lose that fix — which is exactly the experience that makes people
     * stop trusting automated planning.
     */
    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    /** Distinguishes the seats within one shift; 0-based. */
    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    protected ShiftAssignment() {
        // for JPA
    }

    public ShiftAssignment(UUID organizationId, UUID scheduleId, UUID shiftId, int slotIndex) {
        setOrganizationId(organizationId);
        this.scheduleId = scheduleId;
        this.shiftId = shiftId;
        this.slotIndex = slotIndex;
    }

    public boolean isFilled() {
        return employeeId != null;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }

    public UUID getShiftId() {
        return shiftId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public int getSlotIndex() {
        return slotIndex;
    }
}
