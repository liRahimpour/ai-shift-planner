package com.aishiftplanner.scheduler.schedule.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.aishiftplanner.scheduler.shared.domain.TimeWindow;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The planning entity: one seat in one shift, whose occupant the solver decides.
 *
 * <p>Modelling a <em>seat</em> rather than a shift is what lets "4 people needed on Saturday
 * evening" be expressed as four independent decisions, each of which can be pinned, left
 * empty, or reassigned without disturbing the others.
 *
 * <p>{@code allowsUnassigned = true} on the planning variable is deliberate. The alternative
 * — forcing every seat to be filled — makes an under-resourced week simply unsolvable, and
 * the solver's only possible answer becomes "no". A manager needs the opposite: the best
 * possible plan, plus an explicit list of the seats that could not be filled. Unfilled seats
 * are then penalized hard in the constraints, so the solver still fills everything it can.
 */
@PlanningEntity
public class ShiftSlot {

    @PlanningId
    private UUID id;

    /** The shift this seat belongs to. A problem fact — never changed by the solver. */
    private PlanningShift shift;

    private int slotIndex;

    /**
     * Pinned seats are a manager's explicit decision and are excluded from the search, so a
     * hand-made fix survives a re-optimization untouched.
     */
    @PlanningPin
    private boolean pinned;

    @PlanningVariable(valueRangeProviderRefs = "employeeRange", allowsUnassigned = true)
    private PlanningEmployee employee;

    /** Required by Timefold for cloning. */
    public ShiftSlot() {
    }

    public ShiftSlot(UUID id, PlanningShift shift, int slotIndex) {
        this.id = id;
        this.shift = shift;
        this.slotIndex = slotIndex;
    }

    // --- Convenience accessors used by the constraint streams -----------------

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

    /** True if this seat and {@code other} cannot both be worked by the same person. */
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
        return "ShiftSlot{" + shift.date() + " " + shift.startTime() + "-" + shift.endTime()
                + " #" + slotIndex + " → "
                + (employee == null ? "unassigned" : employee.fullName()) + "}";
    }
}
