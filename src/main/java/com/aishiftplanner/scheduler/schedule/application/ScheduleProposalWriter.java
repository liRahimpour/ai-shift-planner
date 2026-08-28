package com.aishiftplanner.scheduler.schedule.application;

import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.schedule.domain.PlanningStrategy;
import com.aishiftplanner.scheduler.schedule.domain.Schedule;
import com.aishiftplanner.scheduler.schedule.domain.ShiftAssignment;
import com.aishiftplanner.scheduler.schedule.infrastructure.ScheduleRepository;
import com.aishiftplanner.scheduler.schedule.infrastructure.ShiftAssignmentRepository;
import com.aishiftplanner.scheduler.schedule.solver.ShiftSchedule;
import com.aishiftplanner.scheduler.schedule.solver.ShiftSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one solved proposal to the database.
 *
 * <p>Its own bean and its own transaction so that a failure while persisting the third
 * strategy does not roll back the first two — a manager with two of three options is far
 * better off than one with none.
 */
@Service
public class ScheduleProposalWriter {

    private static final Logger log = LoggerFactory.getLogger(ScheduleProposalWriter.class);

    private final ScheduleRepository scheduleRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final ScheduleMetricsCalculator metricsCalculator;

    public ScheduleProposalWriter(
            ScheduleRepository scheduleRepository,
            ShiftAssignmentRepository assignmentRepository,
            ScheduleMetricsCalculator metricsCalculator) {
        this.scheduleRepository = scheduleRepository;
        this.assignmentRepository = assignmentRepository;
        this.metricsCalculator = metricsCalculator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID persist(PlanningPeriod period, PlanningStrategy strategy, ShiftSchedule solved) {
        // Replacing any previous proposal for this strategy keeps at most one row per
        // (period, strategy), so re-running does not leave stale options a manager could
        // pick from by accident.
        scheduleRepository
                .findByPlanningPeriodIdAndStrategy(period.getId(), strategy)
                .ifPresent(existing -> {
                    assignmentRepository.deleteAllByScheduleId(existing.getId());
                    scheduleRepository.delete(existing);
                    scheduleRepository.flush();
                });

        Schedule schedule = new Schedule(period.getOrganizationId(), period.getId(), strategy);
        schedule.setHardScore(solved.getScore() == null ? 0L : solved.getScore().hardScore());
        schedule.setSoftScore(solved.getScore() == null ? 0L : solved.getScore().softScore());

        ScheduleMetricsCalculator.Metrics metrics = metricsCalculator.calculate(solved);
        schedule.setTotalStaffCost(metrics.totalStaffCost());
        schedule.setPreferenceSatisfaction(metrics.preferenceSatisfaction());
        schedule.setContractHoursDeviation(metrics.contractHoursDeviation());
        schedule.setUnfilledPositions(metrics.unfilledPositions());
        schedule.setOvertimeHours(metrics.overtimeHours());
        schedule.setFairnessScore(metrics.fairnessScore());

        Schedule savedSchedule = scheduleRepository.save(schedule);

        List<ShiftAssignment> assignments = new ArrayList<>(solved.getSlots().size());
        for (ShiftSlot slot : solved.getSlots()) {
            ShiftAssignment assignment = new ShiftAssignment(
                    period.getOrganizationId(),
                    savedSchedule.getId(),
                    slot.getShift().id(),
                    slot.getSlotIndex());
            assignment.setEmployeeId(slot.isFilled() ? slot.getEmployee().id() : null);
            assignment.setPinned(slot.isPinned());
            assignments.add(assignment);
        }
        assignmentRepository.saveAll(assignments);

        log.info(
                "Persisted {} proposal for period {}: score={} cost={} unfilled={}",
                strategy,
                period.getId(),
                solved.getScore(),
                metrics.totalStaffCost(),
                metrics.unfilledPositions());

        return savedSchedule.getId();
    }
}
