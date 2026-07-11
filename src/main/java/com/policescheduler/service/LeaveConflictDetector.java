package com.policescheduler.service;

import com.policescheduler.entity.CycleDutyAssignment;
import com.policescheduler.entity.LeaveRequest;
import com.policescheduler.repository.CycleDutyAssignmentRepository;
import com.policescheduler.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Detects leave conflicts for cycle duty assignments.
 * A conflict exists when an assigned person has an APPROVED leave request
 * overlapping their assignment date.
 */
@Service
public class LeaveConflictDetector {

    private final LeaveRequestRepository leaveRequestRepository;
    private final CycleDutyAssignmentRepository cycleDutyAssignmentRepository;

    public LeaveConflictDetector(LeaveRequestRepository leaveRequestRepository,
                                 CycleDutyAssignmentRepository cycleDutyAssignmentRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.cycleDutyAssignmentRepository = cycleDutyAssignmentRepository;
    }

    /**
     * Checks if a person has an approved leave request overlapping the given date.
     *
     * @param personId the ID of the personnel to check
     * @param date     the date to check for leave overlap
     * @return true if the person is on approved leave for the given date
     */
    public boolean isOnLeave(Long personId, LocalDate date) {
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedOverlapping(
                personId, date, date);
        return !approvedLeaves.isEmpty();
    }

    /**
     * Returns all duty assignments for a cycle where the assigned person
     * is on approved leave on the assignment date.
     *
     * @param cycleId the ID of the cycle to check
     * @return list of CycleDutyAssignment records that conflict with approved leave
     */
    public List<CycleDutyAssignment> getConflicts(Long cycleId) {
        List<CycleDutyAssignment> assignments = cycleDutyAssignmentRepository.findByCycleId(cycleId);

        return assignments.stream()
                .filter(assignment -> isOnLeave(assignment.getPersonId(), assignment.getDate()))
                .collect(Collectors.toList());
    }
}
