package com.policescheduler.service;

import com.policescheduler.dto.*;
import com.policescheduler.entity.LeaveRequest;
import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.User;
import com.policescheduler.entity.CycleDutyAssignment;
import com.policescheduler.repository.CycleDutyAssignmentRepository;
import com.policescheduler.repository.LeaveRequestRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LeaveService {

    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final PersonnelRepository personnelRepository;
    private final UserRepository userRepository;
    private final CycleDutyAssignmentRepository cycleDutyAssignmentRepository;

    public LeaveService(LeaveRequestRepository leaveRequestRepository,
                        PersonnelRepository personnelRepository,
                        UserRepository userRepository,
                        CycleDutyAssignmentRepository cycleDutyAssignmentRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.personnelRepository = personnelRepository;
        this.userRepository = userRepository;
        this.cycleDutyAssignmentRepository = cycleDutyAssignmentRepository;
    }

    public Page<LeaveRequestDto> listRequests(LeaveFilter filter, Pageable pageable,
                                               String username, String role) {
        Specification<LeaveRequest> spec = buildSpecification(filter, username, role);
        return leaveRequestRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional
    public LeaveRequestDto submit(CreateLeaveRequest request) {
        Personnel personnel = personnelRepository.findById(request.getPersonnelId())
                .orElseThrow(() -> new EntityNotFoundException("Personnel not found: " + request.getPersonnelId()));

        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                request.getPersonnelId(), request.getStartDate(), request.getEndDate());
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("Overlapping leave request exists");
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setPersonnelId(request.getPersonnelId());
        leave.setLeaveType(request.getLeaveType());
        leave.setStartDate(request.getStartDate());
        leave.setEndDate(request.getEndDate());
        leave.setReason(request.getReason());
        leave.setStatus("PENDING");

        LeaveRequest saved = leaveRequestRepository.save(leave);
        log.info("Leave request submitted for personnel ID: {}", request.getPersonnelId());
        return toDto(saved);
    }

    @Transactional
    public LeaveRequestDto approve(Long id, String approverUsername) {
        LeaveRequest leave = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + id));

        if (!"PENDING".equals(leave.getStatus())) {
            throw new IllegalStateException("Leave request already processed");
        }

        User approver = userRepository.findByUsername(approverUsername)
                .orElseThrow(() -> new EntityNotFoundException("Approver not found"));

        leave.setStatus("APPROVED");
        leave.setApprovedBy(approver.getId());
        LeaveRequest saved = leaveRequestRepository.save(leave);
        log.info("Leave request {} approved by {}", id, approverUsername);
        return toDto(saved);
    }

    @Transactional
    public LeaveRequestDto reject(Long id, String approverUsername, String reason) {
        LeaveRequest leave = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + id));

        if (!"PENDING".equals(leave.getStatus())) {
            throw new IllegalStateException("Leave request already processed");
        }

        User approver = userRepository.findByUsername(approverUsername)
                .orElseThrow(() -> new EntityNotFoundException("Approver not found"));

        leave.setStatus("REJECTED");
        leave.setApprovedBy(approver.getId());
        leave.setRejectionReason(reason);
        LeaveRequest saved = leaveRequestRepository.save(leave);
        log.info("Leave request {} rejected by {}", id, approverUsername);
        return toDto(saved);
    }

    @Transactional
    public LeaveRequestDto cancel(Long id, String username) {
        LeaveRequest leave = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + id));

        // Only PENDING or APPROVED (future/current) leaves can be cancelled
        if ("REJECTED".equals(leave.getStatus()) || "CANCELLED".equals(leave.getStatus())) {
            throw new IllegalStateException("Leave request cannot be cancelled");
        }

        // Cannot cancel past leaves (end date already passed)
        if (leave.getEndDate().isBefore(java.time.LocalDate.now())) {
            throw new IllegalStateException("Cannot cancel past leave requests");
        }

        leave.setStatus("CANCELLED");
        LeaveRequest saved = leaveRequestRepository.save(leave);
        log.info("Leave request {} cancelled by {}", id, username);
        return toDto(saved);
    }

    private Specification<LeaveRequest> buildSpecification(LeaveFilter filter, String username, String role) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Role-based filtering
            if ("GENERAL_USER".equals(role)) {
                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null && user.getPersonnelId() != null) {
                    predicates.add(cb.equal(root.get("personnelId"), user.getPersonnelId()));
                } else {
                    predicates.add(cb.equal(root.get("personnelId"), -1L)); // no results
                }
            }
            // HIGHER_OFFICER and ADMIN see all (ADMIN sees all, HIGHER_OFFICER sees subordinates - simplified to all for now)

            if (filter != null) {
                if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                    predicates.add(cb.equal(root.get("status"), filter.getStatus()));
                }
                if (filter.getLeaveType() != null && !filter.getLeaveType().isBlank()) {
                    predicates.add(cb.equal(root.get("leaveType"), filter.getLeaveType()));
                }
                if (filter.getPersonnelId() != null) {
                    predicates.add(cb.equal(root.get("personnelId"), filter.getPersonnelId()));
                }
                if (filter.getStartDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), filter.getStartDate()));
                }
                if (filter.getEndDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), filter.getEndDate()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private LeaveRequestDto toDto(LeaveRequest lr) {
        Personnel p = personnelRepository.findById(lr.getPersonnelId()).orElse(null);
        return LeaveRequestDto.builder()
                .id(lr.getId())
                .personnelId(lr.getPersonnelId())
                .personnelName(p != null ? p.getPersonName() : null)
                .badgeId(p != null ? p.getBadgeId() : null)
                .leaveType(lr.getLeaveType())
                .startDate(lr.getStartDate())
                .endDate(lr.getEndDate())
                .status(lr.getStatus())
                .reason(lr.getReason())
                .approvedBy(lr.getApprovedBy())
                .rejectionReason(lr.getRejectionReason())
                .createdAt(lr.getCreatedAt())
                .updatedAt(lr.getUpdatedAt())
                .build();
    }

    /**
     * Finds duty assignments that conflict with the given leave dates for a person.
     * Returns a list of conflict details including assignmentId, date, dutyName, and platoonId.
     */
    public List<Map<String, Object>> findDutyConflicts(Long personnelId, LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> conflicts = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            List<CycleDutyAssignment> dayAssignments = cycleDutyAssignmentRepository.findByPersonIdAndDate(personnelId, current);
            for (CycleDutyAssignment assignment : dayAssignments) {
                Map<String, Object> conflict = new java.util.LinkedHashMap<>();
                conflict.put("assignmentId", assignment.getId());
                conflict.put("date", assignment.getDate().toString());
                conflict.put("dutyName", assignment.getShiftType());
                conflict.put("platoonId", assignment.getPlatoonId());
                conflicts.add(conflict);
            }
            current = current.plusDays(1);
        }
        return conflicts;
    }
}
