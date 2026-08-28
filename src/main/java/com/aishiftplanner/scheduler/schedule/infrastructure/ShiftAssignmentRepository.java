package com.aishiftplanner.scheduler.schedule.infrastructure;

import com.aishiftplanner.scheduler.schedule.domain.ShiftAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

    Optional<ShiftAssignment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<ShiftAssignment> findAllByScheduleId(UUID scheduleId);

    List<ShiftAssignment> findAllByScheduleIdAndShiftId(UUID scheduleId, UUID shiftId);

    List<ShiftAssignment> findAllByScheduleIdAndEmployeeId(UUID scheduleId, UUID employeeId);

    /** Pinned assignments survive re-optimization; a regenerate reads them back from here. */
    List<ShiftAssignment> findAllByScheduleIdAndPinnedTrue(UUID scheduleId);

    void deleteAllByScheduleId(UUID scheduleId);
}
