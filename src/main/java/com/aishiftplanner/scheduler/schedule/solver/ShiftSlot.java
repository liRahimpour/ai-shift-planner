package com.aishiftplanner.scheduler.schedule.solver;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The planning entity: one seat in one shift, whose occupant the solver decides.
 *
 * <p>Modelling a seat rather than an entire shift allows a requirement such as
 * "4 employees needed Saturday evening" to be represented as four independent planning
 * decisions.
 *
 * <p>{@code allowsUnassigned = true} is deliberate. If the business does not have enough
 * employees, Timefold can still return the best possible schedule and leave individual
 * seats explicitly unassigned instead of declaring the entire problem unsolvable.
 */
@PlanningEntity
public class ShiftSlot {

    @PlanningId
    private UUID id;

    /**
     * The shift this seat belongs to.
     * This is a problem fact and is never changed by the solver.
     */
    private PlanningShift shift;

    private int slotIndex;

    /**
     * Pinned seats represent an explicit manager decision and must not be changed by the
     * solver during re-optimization.
     */
    @PlanningPin
    private boolean pinned;

    /**
     * The actual planning variable.
     *
     * <p>Timefold chooses an employee from the employeeRange declared on ShiftSchedule.
     */
    @PlanningVariable(
            valueRangeProviderRefs = "employeeRange",
            allowsUnassigned = true)
    private PlanningEmployee employee;

    /**
     * Required by Timefold for cloning.
     */
    public ShiftSlot() {
    }

    public ShiftSlot(
            UUID id,
            PlanningShift shift,
            int slotIndex) {

        this.id = id;
        this.shift = shift;
        this.slotIndex = slotIndex;
    }

    public TimeWindow window() {
        return shift.window();
    }

    public LocalDate date() {
        return shift.date();
    }

    public double durationHours() {
        return shift.durationHours();
    }

    public boolean isFilled() {
        return employee != null;
    }

    /**
     * True if this seat and another seat overlap in time.
     */
    public boolean overlapsInTime(ShiftSlot other) {
        return window().overlaps(other.window());
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PlanningShift getShift() {
        return shift;
    }

    public void setShift(PlanningShift shift) {
        this.shift = shift;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public PlanningEmployee getEmployee() {
        return employee;
    }

    public void setEmployee(PlanningEmployee employee) {
        this.employee = employee;
    }

    @Override
    public String toString() {
        return "ShiftSlot{"
                + shift.date()
                + " "
                + shift.startTime()
                + "-"
                + shift.endTime()
                + " #"
                + slotIndex
                + " → "
                + (employee == null
                ? "unassigned"
                : employee.fullName())
                + "}";
    }
}