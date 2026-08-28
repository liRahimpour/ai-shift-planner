package com.aishiftplanner.scheduler.schedule.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One proposed (or published) schedule for a planning period.
 *
 * <p>A planning run produces three of these over the same set of shifts — one per strategy —
 * so "compare the options, then pick one" is a cheap read rather than three solver runs.
 * The metrics are stored alongside because a manager comparing proposals needs the numbers
 * instantly, and recomputing them on every page load would be both slow and a chance for the
 * displayed figure to drift from the one the solver actually optimized.
 */
@Entity
@Table(name = "schedules")
public class Schedule extends TenantScopedEntity {

    @Column(name = "planning_period_id", nullable = false)
    private UUID planningPeriodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 30)
    private PlanningStrategy strategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.DRAFT;

    /** At most one schedule per planning period may be selected (enforced by a partial index). */
    @Column(name = "selected", nullable = false)
    private boolean selected;

    /** Raw solver score. Negative hard score means at least one hard constraint is violated. */
    @Column(name = "hard_score", nullable = false)
    private long hardScore;

    @Column(name = "soft_score", nullable = false)
    private long softScore;

    @Column(name = "total_staff_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalStaffCost = BigDecimal.ZERO;

    /** Percentage of PREFERRED windows that were honoured, 0–100. */
    @Column(name = "preference_satisfaction", nullable = false, precision = 5, scale = 2)
    private BigDecimal preferenceSatisfaction = BigDecimal.ZERO;

    /** Mean absolute deviation from contract hours, as a percentage. */
    @Column(name = "contract_hours_deviation", nullable = false, precision = 5, scale = 2)
    private BigDecimal contractHoursDeviation = BigDecimal.ZERO;

    @Column(name = "unfilled_positions", nullable = false)
    private int unfilledPositions;

    @Column(name = "overtime_hours", nullable = false, precision = 7, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    /** 0–100; how evenly unpopular shifts are spread. */
    @Column(name = "fairness_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal fairnessScore = BigDecimal.ZERO;

    protected Schedule() {
        // for JPA
    }

    public Schedule(UUID organizationId, UUID planningPeriodId, PlanningStrategy strategy) {
        setOrganizationId(organizationId);
        this.planningPeriodId = planningPeriodId;
        this.strategy = strategy;
    }

    /**
     * A schedule is only publishable when no hard constraint is violated.
     *
     * <p>Timefold reports hard-constraint breaches as a negative hard score. Publishing such a
     * schedule would mean putting someone on a shift they are unavailable for, unqualified
     * for, or legally not rested for — so this is checked at the domain level rather than
     * left to the UI to discourage.
     */
    public boolean isFeasible() {
        return hardScore >= 0;
    }

    public boolean canBePublished() {
        return isFeasible() && unfilledPositions == 0;
    }

    public UUID getPlanningPeriodId() {
        return planningPeriodId;
    }

    public PlanningStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(PlanningStrategy strategy) {
        this.strategy = strategy;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public long getHardScore() {
        return hardScore;
    }

    public void setHardScore(long hardScore) {
        this.hardScore = hardScore;
    }

    public long getSoftScore() {
        return softScore;
    }

    public void setSoftScore(long softScore) {
        this.softScore = softScore;
    }

    public BigDecimal getTotalStaffCost() {
        return totalStaffCost;
    }

    public void setTotalStaffCost(BigDecimal totalStaffCost) {
        this.totalStaffCost = totalStaffCost;
    }

    public BigDecimal getPreferenceSatisfaction() {
        return preferenceSatisfaction;
    }

    public void setPreferenceSatisfaction(BigDecimal preferenceSatisfaction) {
        this.preferenceSatisfaction = preferenceSatisfaction;
    }

    public BigDecimal getContractHoursDeviation() {
        return contractHoursDeviation;
    }

    public void setContractHoursDeviation(BigDecimal contractHoursDeviation) {
        this.contractHoursDeviation = contractHoursDeviation;
    }

    public int getUnfilledPositions() {
        return unfilledPositions;
    }

    public void setUnfilledPositions(int unfilledPositions) {
        this.unfilledPositions = unfilledPositions;
    }

    public BigDecimal getOvertimeHours() {
        return overtimeHours;
    }

    public void setOvertimeHours(BigDecimal overtimeHours) {
        this.overtimeHours = overtimeHours;
    }

    public BigDecimal getFairnessScore() {
        return fairnessScore;
    }

    public void setFairnessScore(BigDecimal fairnessScore) {
        this.fairnessScore = fairnessScore;
    }
}
