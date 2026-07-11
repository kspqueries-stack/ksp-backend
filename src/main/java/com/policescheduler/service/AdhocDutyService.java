package com.policescheduler.service;

import com.policescheduler.dto.AdhocAssigneeInfo;
import com.policescheduler.dto.AdhocDutyResponse;
import com.policescheduler.dto.CreateAdhocDutyRequest;
import com.policescheduler.entity.AdhocDuty;
import com.policescheduler.entity.AdhocDutyAssignment;
import com.policescheduler.entity.CycleDutyAssignment;
import com.policescheduler.entity.Personnel;
import com.policescheduler.repository.AdhocDutyAssignmentRepository;
import com.policescheduler.repository.AdhocDutyRepository;
import com.policescheduler.repository.CycleDutyAssignmentRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdhocDutyService {

    private final AdhocDutyRepository adhocDutyRepository;
    private final AdhocDutyAssignmentRepository adhocDutyAssignmentRepository;
    private final CycleDutyAssignmentRepository cycleDutyAssignmentRepository;
    private final PersonnelRepository personnelRepository;
    private final LeaveConflictDetector leaveConflictDetector;
    private final CycleAuditService cycleAuditService;
    private final ObjectMapper objectMapper;

    public AdhocDutyService(AdhocDutyRepository adhocDutyRepository,
                            AdhocDutyAssignmentRepository adhocDutyAssignmentRepository,
                            CycleDutyAssignmentRepository cycleDutyAssignmentRepository,
                            PersonnelRepository personnelRepository,
                            LeaveConflictDetector leaveConflictDetector,
                            CycleAuditService cycleAuditService,
                            ObjectMapper objectMapper) {
        this.adhocDutyRepository = adhocDutyRepository;
        this.adhocDutyAssignmentRepository = adhocDutyAssignmentRepository;
        this.cycleDutyAssignmentRepository = cycleDutyAssignmentRepository;
        this.personnelRepository = personnelRepository;
        this.leaveConflictDetector = leaveConflictDetector;
        this.cycleAuditService = cycleAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdhocDutyResponse createAdhocDuty(CreateAdhocDutyRequest request) {
        // Validate
        if (request.date().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Adhoc duty date must be today or in the future");
        }
        if (request.requiredCount() < 1) {
            throw new IllegalArgumentException("Required count must be at least 1");
        }

        // Determine personnel: manual selection or auto-pick
        List<PersonnelPickResult> picked;
        if (request.personnelIds() != null && !request.personnelIds().isEmpty()) {
            // Manual personnel selection
            picked = buildManualPersonnelList(request.personnelIds(), request.date());
        } else {
            // Existing auto-pick logic
            picked = pickPersonnel(
                request.date(), request.startTime(), request.endTime(),
                request.requiredCount(),
                request.pickFrom() != null ? request.pickFrom() : "ALL_PLATOONS",
                request.platoonIds()
            );
        }

        // Create AdhocDuty record
        AdhocDuty duty = new AdhocDuty();
        duty.setDutyName(request.dutyName());
        duty.setDate(request.date());
        duty.setStartTime(request.startTime());
        duty.setEndTime(request.endTime());
        duty.setRequiredCount(request.requiredCount());
        duty.setActualCount(picked.size());
        duty.setPickFrom(request.pickFrom() != null ? request.pickFrom() : "ALL_PLATOONS");
        if (request.platoonIds() != null && !request.platoonIds().isEmpty()) {
            try {
                duty.setPlatoonIds(objectMapper.writeValueAsString(request.platoonIds()));
            } catch (Exception e) {
                duty.setPlatoonIds(null);
            }
        }
        duty.setLocation(request.location());
        duty.setStatus("ACTIVE");
        duty.setCreatedAt(LocalDateTime.now());
        duty.setUpdatedAt(LocalDateTime.now());
        AdhocDuty savedDuty = adhocDutyRepository.save(duty);

        // Create AdhocDutyAssignment records and update CycleDutyAssignment
        List<AdhocAssigneeInfo> assignees = new ArrayList<>();
        for (PersonnelPickResult result : picked) {
            AdhocDutyAssignment adhocAssignment = new AdhocDutyAssignment();
            adhocAssignment.setAdhocDutyId(savedDuty.getId());
            adhocAssignment.setPersonId(result.personId);
            adhocAssignment.setOriginalAssignmentId(result.assignmentId);
            adhocAssignment.setOriginalShift(result.originalShift);
            adhocAssignment.setOriginalDutyName(result.originalDutyName);
            adhocAssignment.setStatus("ASSIGNED");
            adhocAssignment.setCreatedAt(LocalDateTime.now());
            adhocDutyAssignmentRepository.save(adhocAssignment);

            // Update the original CycleDutyAssignment AND any other assignments for this person on same date
            if (result.assignmentId != null) {
                Long savedDutyId = savedDuty.getId();
                // Update the picked assignment
                cycleDutyAssignmentRepository.findById(result.assignmentId).ifPresent(cda -> {
                    cda.setStatus("COVERED_BY_ADHOC");
                    cda.setAdhocDutyId(savedDutyId);
                    cycleDutyAssignmentRepository.save(cda);
                });
                // Also update any other active assignments for this person on the same date (across all cycles)
                List<CycleDutyAssignment> otherAssignments = cycleDutyAssignmentRepository
                    .findByPersonIdAndDate(result.personId, request.date());
                for (CycleDutyAssignment other : otherAssignments) {
                    if (!other.getId().equals(result.assignmentId) &&
                        (other.getStatus() == null || "ACTIVE".equals(other.getStatus()))) {
                        other.setStatus("COVERED_BY_ADHOC");
                        other.setAdhocDutyId(savedDutyId);
                        cycleDutyAssignmentRepository.save(other);
                    }
                }
            }

            assignees.add(new AdhocAssigneeInfo(
                result.personId, result.personName, result.badgeId,
                result.designation, result.section,
                result.originalShift, result.originalDutyName,
                result.disruptionScore, "ASSIGNED"
            ));
        }

        // Audit
        cycleAuditService.log(null, "ADHOC_CREATED",
            "Adhoc duty '" + savedDuty.getDutyName() + "' created with " + picked.size() + " personnel on " + savedDuty.getDate(),
            null, String.valueOf(savedDuty.getId()));

        return buildResponse(savedDuty, assignees);
    }

    public List<AdhocDutyResponse> listAdhocDuties(LocalDate date, String status) {
        List<AdhocDuty> duties;
        if (date != null && status != null) {
            duties = adhocDutyRepository.findByDateAndStatus(date, status);
        } else if (date != null) {
            duties = adhocDutyRepository.findByDateAndStatusNot(date, "CANCELLED");
        } else if (status != null) {
            duties = adhocDutyRepository.findByStatus(status);
        } else {
            duties = adhocDutyRepository.findAllByOrderByDateDescCreatedAtDesc();
        }
        return duties.stream()
            .map(duty -> buildResponse(duty, getAssigneesForDuty(duty.getId())))
            .collect(Collectors.toList());
    }

    public AdhocDutyResponse getAdhocDutyDetails(Long id) {
        AdhocDuty duty = adhocDutyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Adhoc duty not found: " + id));
        return buildResponse(duty, getAssigneesForDuty(id));
    }

    @Transactional
    public AdhocDutyResponse cancelAdhocDuty(Long id) {
        AdhocDuty duty = adhocDutyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Adhoc duty not found: " + id));

        duty.setStatus("CANCELLED");
        duty.setUpdatedAt(LocalDateTime.now());
        adhocDutyRepository.save(duty);

        // Revert all ASSIGNED assignments
        List<AdhocDutyAssignment> assignments = adhocDutyAssignmentRepository
            .findByAdhocDutyIdAndStatus(id, "ASSIGNED");
        for (AdhocDutyAssignment assignment : assignments) {
            assignment.setStatus("CANCELLED");
            adhocDutyAssignmentRepository.save(assignment);

            // Restore original CycleDutyAssignment
            if (assignment.getOriginalAssignmentId() != null) {
                cycleDutyAssignmentRepository.findById(assignment.getOriginalAssignmentId()).ifPresent(cda -> {
                    cda.setStatus("ACTIVE");
                    cda.setAdhocDutyId(null);
                    cycleDutyAssignmentRepository.save(cda);
                });
            }
        }

        // Audit
        cycleAuditService.log(null, "ADHOC_CANCELLED",
            "Adhoc duty '" + duty.getDutyName() + "' cancelled, " + assignments.size() + " personnel released",
            String.valueOf(id), null);

        return buildResponse(duty, getAssigneesForDuty(id));
    }

    public List<AdhocAssigneeInfo> previewAvailablePersonnel(LocalDate date, LocalTime startTime,
                                                              LocalTime endTime, int count,
                                                              String pickFrom, List<Long> platoonIds) {
        List<PersonnelPickResult> results = pickPersonnel(date, startTime, endTime, count, pickFrom, platoonIds);
        return results.stream()
            .map(r -> new AdhocAssigneeInfo(
                r.personId, r.personName, r.badgeId, r.designation, r.section,
                r.originalShift, r.originalDutyName, r.disruptionScore, "PREVIEW"
            ))
            .collect(Collectors.toList());
    }

    public List<AdhocAssigneeInfo> searchAvailablePersonnel(String query, LocalDate date,
                                                              LocalTime startTime, LocalTime endTime) {
        List<Personnel> matches = personnelRepository.searchByNameOrBadgeId(query, PageRequest.of(0, 20));

        List<AdhocAssigneeInfo> results = new ArrayList<>();
        for (Personnel person : matches) {
            Long personId = person.getId();

            // Filter out personnel on leave
            if (leaveConflictDetector.isOnLeave(personId, date)) {
                continue;
            }

            // Filter out personnel already assigned to active adhoc duty on same date
            List<AdhocDutyAssignment> existingAdhoc = adhocDutyAssignmentRepository
                .findByPersonIdAndStatus(personId, "ASSIGNED");
            boolean alreadyOnAdhoc = existingAdhoc.stream().anyMatch(a -> {
                AdhocDuty existingDuty = adhocDutyRepository.findById(a.getAdhocDutyId()).orElse(null);
                return existingDuty != null && existingDuty.getDate().equals(date)
                    && !"CANCELLED".equals(existingDuty.getStatus());
            });
            if (alreadyOnAdhoc) continue;

            // Look up current shift assignment for the date
            String shiftType = null;
            String dutyName = null;
            List<CycleDutyAssignment> assignments = cycleDutyAssignmentRepository.findByPersonIdAndDate(personId, date);
            if (!assignments.isEmpty()) {
                CycleDutyAssignment firstActive = assignments.stream()
                    .filter(a -> a.getStatus() == null || "ACTIVE".equals(a.getStatus()))
                    .findFirst().orElse(assignments.get(0));
                shiftType = firstActive.getShiftType();
                dutyName = firstActive.getDutyName();
            }

            results.add(new AdhocAssigneeInfo(
                personId, person.getPersonName(), person.getBadgeId(),
                person.getDesignation(), person.getSection(),
                shiftType, dutyName,
                0, "AVAILABLE"
            ));
        }

        return results;
    }

    // --- Private helper methods ---

    private List<PersonnelPickResult> buildManualPersonnelList(List<Long> personnelIds, LocalDate date) {
        List<PersonnelPickResult> results = new ArrayList<>();
        for (Long personId : personnelIds) {
            // Validate personnel exists
            Personnel person = personnelRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Personnel not found with ID: " + personId));

            // Validate not on leave
            if (leaveConflictDetector.isOnLeave(personId, date)) {
                throw new IllegalArgumentException("Personnel '" + person.getPersonName() + "' (ID: " + personId + ") is on leave on " + date);
            }

            // Validate not already on another adhoc duty for this date
            List<AdhocDutyAssignment> existingAdhoc = adhocDutyAssignmentRepository
                .findByPersonIdAndStatus(personId, "ASSIGNED");
            boolean alreadyOnAdhoc = existingAdhoc.stream().anyMatch(a -> {
                AdhocDuty existingDuty = adhocDutyRepository.findById(a.getAdhocDutyId()).orElse(null);
                return existingDuty != null && existingDuty.getDate().equals(date)
                    && !"CANCELLED".equals(existingDuty.getStatus());
            });
            if (alreadyOnAdhoc) {
                throw new IllegalArgumentException("Personnel '" + person.getPersonName() + "' (ID: " + personId + ") is already assigned to an active adhoc duty on " + date);
            }

            // Look up current shift assignment for the date
            String shiftType = null;
            String dutyName = null;
            Long assignmentId = null;
            List<CycleDutyAssignment> assignments = cycleDutyAssignmentRepository.findByPersonIdAndDate(personId, date);
            if (!assignments.isEmpty()) {
                CycleDutyAssignment firstActive = assignments.stream()
                    .filter(a -> a.getStatus() == null || "ACTIVE".equals(a.getStatus()))
                    .findFirst().orElse(assignments.get(0));
                shiftType = firstActive.getShiftType();
                dutyName = firstActive.getDutyName();
                assignmentId = firstActive.getId();
            }

            results.add(new PersonnelPickResult(
                personId, person.getPersonName(), person.getBadgeId(),
                person.getDesignation(), person.getSection(),
                shiftType, dutyName,
                0, assignmentId
            ));
        }
        return results;
    }

    private List<PersonnelPickResult> pickPersonnel(LocalDate date, LocalTime adhocStart,
                                                     LocalTime adhocEnd, int count,
                                                     String pickFrom, List<Long> platoonIds) {
        // 1. Get all active cycle assignments for this date
        List<CycleDutyAssignment> assignments = getAllActiveAssignmentsForDate(date);

        // 2. Filter and score candidates from cycle assignments
        List<PersonnelPickResult> candidates = new ArrayList<>();
        Map<Long, Personnel> personnelCache = new HashMap<>();
        Set<Long> processedPersonIds = new HashSet<>();

        for (CycleDutyAssignment assignment : assignments) {
            // Skip non-ACTIVE
            if (assignment.getStatus() != null && !"ACTIVE".equals(assignment.getStatus())) {
                continue;
            }

            Long personId = assignment.getPersonId();

            // Skip duplicates (same person may have multiple assignments)
            if (!processedPersonIds.add(personId)) continue;

            // Skip if on leave
            if (leaveConflictDetector.isOnLeave(personId, date)) {
                continue;
            }

            // Skip if already on another adhoc duty for this date
            if (isAlreadyOnAdhocDuty(personId, date)) continue;

            // Filter by platoon if SPECIFIC_PLATOONS
            if ("SPECIFIC_PLATOONS".equals(pickFrom) && platoonIds != null && !platoonIds.isEmpty()) {
                if (!platoonIds.contains(assignment.getPlatoonId())) {
                    continue;
                }
            }

            // Calculate disruption score (overlap minutes with current shift)
            int overlapMinutes = calculateOverlapMinutes(assignment.getShiftType(), adhocStart, adhocEnd);

            // Get personnel details
            Personnel person = personnelCache.computeIfAbsent(personId,
                id -> personnelRepository.findById(id).orElse(null));
            if (person == null) continue;

            candidates.add(new PersonnelPickResult(
                personId, person.getPersonName(), person.getBadgeId(),
                person.getDesignation(), person.getSection(),
                assignment.getShiftType(),
                assignment.getDutyName(),
                overlapMinutes,
                assignment.getId()
            ));
        }

        // 3. FALLBACK: If no cycle assignments exist for this date, pick from all active personnel
        if (candidates.isEmpty()) {
            candidates = pickFromAllActivePersonnel(date, adhocStart, adhocEnd, pickFrom, platoonIds);
        }

        // 4. Sort by disruption score ascending (least disruption first)
        candidates.sort(Comparator.comparingInt(r -> r.disruptionScore));

        // 5. Take top N
        return candidates.stream().limit(count).collect(Collectors.toList());
    }

    /**
     * Fallback method: picks from all active personnel when no cycle assignments exist for the date.
     * This handles cases where no rotation cycle covers the requested date.
     */
    private List<PersonnelPickResult> pickFromAllActivePersonnel(LocalDate date, LocalTime adhocStart,
                                                                  LocalTime adhocEnd, String pickFrom,
                                                                  List<Long> platoonIds) {
        List<Personnel> allActive = personnelRepository.findByIsActiveTrue();
        List<PersonnelPickResult> candidates = new ArrayList<>();

        for (Personnel person : allActive) {
            Long personId = person.getId();

            // Skip if on leave
            if (leaveConflictDetector.isOnLeave(personId, date)) {
                continue;
            }

            // Skip if already on another adhoc duty for this date
            if (isAlreadyOnAdhocDuty(personId, date)) continue;

            // Filter by platoon if SPECIFIC_PLATOONS
            if ("SPECIFIC_PLATOONS".equals(pickFrom) && platoonIds != null && !platoonIds.isEmpty()) {
                if (person.getPlatoonId() == null || !platoonIds.contains(person.getPlatoonId())) {
                    continue;
                }
            }

            candidates.add(new PersonnelPickResult(
                personId, person.getPersonName(), person.getBadgeId(),
                person.getDesignation(), person.getSection(),
                null,  // no shift info when no cycle
                null,  // no duty name when no cycle
                0,     // zero disruption since no active shift
                null   // no assignment ID
            ));
        }

        return candidates;
    }

    /**
     * Checks if a person is already assigned to an active adhoc duty on the given date.
     */
    private boolean isAlreadyOnAdhocDuty(Long personId, LocalDate date) {
        List<AdhocDutyAssignment> existingAdhoc = adhocDutyAssignmentRepository
            .findByPersonIdAndStatus(personId, "ASSIGNED");
        return existingAdhoc.stream().anyMatch(a -> {
            AdhocDuty existingDuty = adhocDutyRepository.findById(a.getAdhocDutyId()).orElse(null);
            return existingDuty != null && existingDuty.getDate().equals(date)
                && !"CANCELLED".equals(existingDuty.getStatus());
        });
    }

    private List<CycleDutyAssignment> getAllActiveAssignmentsForDate(LocalDate date) {
        // Include both status='ACTIVE' and status=NULL (old cycles before shift rotation feature)
        return cycleDutyAssignmentRepository.findActiveOrNullStatusByDate(date);
    }

    /**
     * Calculate overlap minutes between a shift and adhoc time window.
     * Handles NIGHT shift midnight wrap and adhoc time wrapping midnight.
     */
    private int calculateOverlapMinutes(String shift, LocalTime adhocStart, LocalTime adhocEnd) {
        if (shift == null) return 0;

        LocalTime shiftStart;
        LocalTime shiftEnd;
        boolean nightShift = false;

        switch (shift) {
            case "DAY" -> { shiftStart = LocalTime.of(6, 0); shiftEnd = LocalTime.of(14, 0); }
            case "AFTERNOON" -> { shiftStart = LocalTime.of(14, 0); shiftEnd = LocalTime.of(22, 0); }
            case "NIGHT" -> { shiftStart = LocalTime.of(22, 0); shiftEnd = LocalTime.of(6, 0); nightShift = true; }
            default -> { return 0; }
        }

        boolean adhocWraps = adhocEnd.isBefore(adhocStart);

        if (!nightShift && !adhocWraps) {
            // Simple case: both are within same day
            LocalTime overlapStart = shiftStart.isAfter(adhocStart) ? shiftStart : adhocStart;
            LocalTime overlapEnd = shiftEnd.isBefore(adhocEnd) ? shiftEnd : adhocEnd;
            if (overlapStart.isBefore(overlapEnd)) {
                return (int) Duration.between(overlapStart, overlapEnd).toMinutes();
            }
            return 0;
        }

        // Handle night shift or adhoc wrapping midnight by splitting into segments
        int totalOverlap = 0;

        if (nightShift) {
            // Night shift segment 1: 22:00-23:59
            totalOverlap += overlapSegment(LocalTime.of(22, 0), LocalTime.of(23, 59), adhocStart, adhocEnd, adhocWraps);
            // Night shift segment 2: 00:00-06:00
            totalOverlap += overlapSegment(LocalTime.of(0, 0), LocalTime.of(6, 0), adhocStart, adhocEnd, adhocWraps);
        } else {
            // Adhoc wraps midnight but shift doesn't
            // Adhoc segment 1: adhocStart-23:59
            totalOverlap += overlapSimple(shiftStart, shiftEnd, adhocStart, LocalTime.of(23, 59));
            // Adhoc segment 2: 00:00-adhocEnd
            totalOverlap += overlapSimple(shiftStart, shiftEnd, LocalTime.of(0, 0), adhocEnd);
        }

        return totalOverlap;
    }

    private int overlapSegment(LocalTime segStart, LocalTime segEnd, LocalTime adhocStart,
                               LocalTime adhocEnd, boolean adhocWraps) {
        if (adhocWraps) {
            // Adhoc: [adhocStart-23:59] + [00:00-adhocEnd]
            int o1 = overlapSimple(segStart, segEnd, adhocStart, LocalTime.of(23, 59));
            int o2 = overlapSimple(segStart, segEnd, LocalTime.of(0, 0), adhocEnd);
            return o1 + o2;
        } else {
            return overlapSimple(segStart, segEnd, adhocStart, adhocEnd);
        }
    }

    private int overlapSimple(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        LocalTime overlapStart = start1.isAfter(start2) ? start1 : start2;
        LocalTime overlapEnd = end1.isBefore(end2) ? end1 : end2;
        if (overlapStart.isBefore(overlapEnd)) {
            return (int) Duration.between(overlapStart, overlapEnd).toMinutes();
        }
        return 0;
    }

    private List<AdhocAssigneeInfo> getAssigneesForDuty(Long adhocDutyId) {
        List<AdhocDutyAssignment> assignments = adhocDutyAssignmentRepository.findByAdhocDutyId(adhocDutyId);
        List<AdhocAssigneeInfo> assignees = new ArrayList<>();
        for (AdhocDutyAssignment a : assignments) {
            Personnel person = personnelRepository.findById(a.getPersonId()).orElse(null);
            if (person != null) {
                assignees.add(new AdhocAssigneeInfo(
                    a.getPersonId(), person.getPersonName(), person.getBadgeId(),
                    person.getDesignation(), person.getSection(),
                    a.getOriginalShift(), a.getOriginalDutyName(),
                    0, a.getStatus()
                ));
            }
        }
        return assignees;
    }

    private AdhocDutyResponse buildResponse(AdhocDuty duty, List<AdhocAssigneeInfo> assignees) {
        List<Long> platoonIdList = null;
        if (duty.getPlatoonIds() != null) {
            try {
                platoonIdList = objectMapper.readValue(duty.getPlatoonIds(), new TypeReference<List<Long>>() {});
            } catch (Exception e) {
                platoonIdList = null;
            }
        }
        return new AdhocDutyResponse(
            duty.getId(), duty.getDutyName(), duty.getDate(),
            duty.getStartTime(), duty.getEndTime(),
            duty.getRequiredCount(), duty.getActualCount(),
            duty.getPickFrom(), platoonIdList,
            duty.getLocation(), duty.getStatus(), duty.getCreatedAt(),
            assignees
        );
    }

    // Internal result class
    private static class PersonnelPickResult {
        final Long personId;
        final String personName;
        final String badgeId;
        final String designation;
        final String section;
        final String originalShift;
        final String originalDutyName;
        final int disruptionScore;
        final Long assignmentId;

        PersonnelPickResult(Long personId, String personName, String badgeId,
                          String designation, String section, String originalShift,
                          String originalDutyName, int disruptionScore, Long assignmentId) {
            this.personId = personId;
            this.personName = personName;
            this.badgeId = badgeId;
            this.designation = designation;
            this.section = section;
            this.originalShift = originalShift;
            this.originalDutyName = originalDutyName;
            this.disruptionScore = disruptionScore;
            this.assignmentId = assignmentId;
        }
    }
}
