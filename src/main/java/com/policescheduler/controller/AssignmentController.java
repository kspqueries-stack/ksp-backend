package com.policescheduler.controller;

import com.policescheduler.dto.ReassignRequest;
import com.policescheduler.dto.ReassignResponse;
import com.policescheduler.entity.AdhocDuty;
import com.policescheduler.entity.CycleDutyAssignment;
import com.policescheduler.entity.Personnel;
import com.policescheduler.repository.AdhocDutyRepository;
import com.policescheduler.repository.CycleDutyAssignmentRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.SectionRepository;
import com.policescheduler.service.CycleAuditService;
import com.policescheduler.service.LeaveConflictDetector;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final CycleDutyAssignmentRepository assignmentRepository;
    private final LeaveConflictDetector leaveConflictDetector;
    private final PersonnelRepository personnelRepository;
    private final SectionRepository sectionRepository;
    private final CycleAuditService cycleAuditService;
    private final AdhocDutyRepository adhocDutyRepository;

    public AssignmentController(CycleDutyAssignmentRepository assignmentRepository,
                                LeaveConflictDetector leaveConflictDetector,
                                PersonnelRepository personnelRepository,
                                SectionRepository sectionRepository,
                                CycleAuditService cycleAuditService,
                                AdhocDutyRepository adhocDutyRepository) {
        this.assignmentRepository = assignmentRepository;
        this.leaveConflictDetector = leaveConflictDetector;
        this.personnelRepository = personnelRepository;
        this.sectionRepository = sectionRepository;
        this.cycleAuditService = cycleAuditService;
        this.adhocDutyRepository = adhocDutyRepository;
    }

    /**
     * Returns assignments for a specific cycle and date, including leave conflict flags.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAssignments(
            @RequestParam Long cycleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<CycleDutyAssignment> assignments = assignmentRepository.findByCycleIdAndDate(cycleId, date);

        // Batch-load all personnel names for efficiency
        var personIds = assignments.stream().map(CycleDutyAssignment::getPersonId).distinct().toList();
        Map<Long, String> personNameMap = new java.util.HashMap<>();
        if (!personIds.isEmpty()) {
            personnelRepository.findAllById(personIds).forEach(p -> {
                String name = p.getPersonName();
                personNameMap.put(p.getId(), (name != null && !name.isBlank()) ? name : p.getBadgeId());
            });
        }

        List<Map<String, Object>> response = assignments.stream()
                .map(assignment -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", assignment.getId());
                    dto.put("cycleId", assignment.getCycleId());
                    dto.put("date", assignment.getDate());
                    dto.put("platoonId", assignment.getPlatoonId());
                    dto.put("sectionId", assignment.getSectionId());
                    dto.put("personId", assignment.getPersonId());
                    dto.put("shiftType", assignment.getShiftType());
                    dto.put("dutyName", assignment.getDutyName());
                    dto.put("groupIndex", assignment.getGroupIndex());
                    dto.put("status", assignment.getStatus() != null ? assignment.getStatus() : "ACTIVE");

                    // If covered by adhoc, include adhoc details
                    if ("COVERED_BY_ADHOC".equals(assignment.getStatus()) && assignment.getAdhocDutyId() != null) {
                        AdhocDuty adhoc = adhocDutyRepository.findById(assignment.getAdhocDutyId()).orElse(null);
                        if (adhoc != null) {
                            dto.put("adhocDutyName", adhoc.getDutyName());
                            dto.put("adhocDutyTime", adhoc.getStartTime().toString() + "-" + adhoc.getEndTime().toString());
                        } else {
                            dto.put("adhocDutyName", null);
                            dto.put("adhocDutyTime", null);
                        }
                    } else {
                        dto.put("adhocDutyName", null);
                        dto.put("adhocDutyTime", null);
                    }

                    dto.put("isOverride", assignment.getIsOverride());
                    dto.put("updatedAt", assignment.getUpdatedAt());
                    dto.put("onLeave", leaveConflictDetector.isOnLeave(
                            assignment.getPersonId(), assignment.getDate()));
                    // Use batch-loaded name, fallback to badge ID
                    dto.put("personName", personNameMap.getOrDefault(assignment.getPersonId(), "Person #" + assignment.getPersonId()));
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Reassigns a duty assignment to a different person.
     * Validates that the replacement is not on leave and has no conflicting assignment.
     */
    @PatchMapping("/reassign")
    public ResponseEntity<?> reassign(@Valid @RequestBody ReassignRequest request) {

        // 1. Find the assignment by ID
        CycleDutyAssignment assignment = assignmentRepository.findById(request.assignmentId())
                .orElse(null);
        if (assignment == null) {
            return ResponseEntity.notFound().build();
        }

        // 2. Check if new person exists
        if (!personnelRepository.existsById(request.newPersonId())) {
            return ResponseEntity.notFound().build();
        }

        // 3. Check if new person is on leave for that date
        if (leaveConflictDetector.isOnLeave(request.newPersonId(), assignment.getDate())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Replacement person is on approved leave for this date"));
        }

        // 4. Check if new person already has assignment for same cycle, date, section
        List<CycleDutyAssignment> duplicates = assignmentRepository
                .findByCycleIdAndDateAndSectionIdAndPersonId(
                        assignment.getCycleId(),
                        assignment.getDate(),
                        assignment.getSectionId(),
                        request.newPersonId());
        if (!duplicates.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Replacement person already has assignment for this cycle/date/section"));
        }

        // 5. Capture old person ID for audit before update
        Long oldPersonId = assignment.getPersonId();

        // 6. Update assignment
        assignment.setPersonId(request.newPersonId());
        assignment.setIsOverride(true);
        CycleDutyAssignment saved = assignmentRepository.save(assignment);

        // 7. Audit log
        cycleAuditService.log(saved.getCycleId(), "DUTY_REASSIGNED",
                "Duty reassigned from Person " + oldPersonId + " to Person " + request.newPersonId() + " on " + saved.getDate(),
                String.valueOf(oldPersonId), String.valueOf(request.newPersonId()));

        // 8. Return ReassignResponse
        ReassignResponse response = new ReassignResponse(
                saved.getId(),
                saved.getCycleId(),
                saved.getDate(),
                saved.getPlatoonId(),
                saved.getSectionId(),
                saved.getPersonId(),
                saved.getShiftType(),
                saved.getIsOverride(),
                saved.getUpdatedAt()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Returns assignments for a specific cycle within a date range (for week/month calendar views).
     */
    @GetMapping("/range")
    public ResponseEntity<List<Map<String, Object>>> getAssignmentsByRange(
            @RequestParam Long cycleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<CycleDutyAssignment> assignments = assignmentRepository.findByCycleIdAndDateBetween(cycleId, startDate, endDate);

        // Batch-load all personnel names for efficiency (avoid N+1)
        var personIds = assignments.stream().map(CycleDutyAssignment::getPersonId).distinct().toList();
        Map<Long, String> personNameMap = new java.util.HashMap<>();
        if (!personIds.isEmpty()) {
            personnelRepository.findAllById(personIds).forEach(p -> {
                String name = p.getPersonName();
                personNameMap.put(p.getId(), (name != null && !name.isBlank()) ? name : p.getBadgeId());
            });
        }

        List<Map<String, Object>> response = assignments.stream()
                .map(assignment -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", assignment.getId());
                    dto.put("cycleId", assignment.getCycleId());
                    dto.put("date", assignment.getDate());
                    dto.put("platoonId", assignment.getPlatoonId());
                    dto.put("sectionId", assignment.getSectionId());
                    dto.put("personId", assignment.getPersonId());
                    dto.put("shiftType", assignment.getShiftType());
                    dto.put("dutyName", assignment.getDutyName());
                    dto.put("groupIndex", assignment.getGroupIndex());
                    dto.put("status", assignment.getStatus() != null ? assignment.getStatus() : "ACTIVE");

                    // If covered by adhoc, include adhoc details
                    if ("COVERED_BY_ADHOC".equals(assignment.getStatus()) && assignment.getAdhocDutyId() != null) {
                        AdhocDuty adhoc = adhocDutyRepository.findById(assignment.getAdhocDutyId()).orElse(null);
                        if (adhoc != null) {
                            dto.put("adhocDutyName", adhoc.getDutyName());
                            dto.put("adhocDutyTime", adhoc.getStartTime().toString() + "-" + adhoc.getEndTime().toString());
                        } else {
                            dto.put("adhocDutyName", null);
                            dto.put("adhocDutyTime", null);
                        }
                    } else {
                        dto.put("adhocDutyName", null);
                        dto.put("adhocDutyTime", null);
                    }

                    dto.put("isOverride", assignment.getIsOverride());
                    dto.put("updatedAt", assignment.getUpdatedAt());
                    dto.put("onLeave", leaveConflictDetector.isOnLeave(
                            assignment.getPersonId(), assignment.getDate()));
                    // Use batch-loaded name, fallback to badge ID
                    dto.put("personName", personNameMap.getOrDefault(assignment.getPersonId(), "Person #" + assignment.getPersonId()));
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns available personnel who can take over a duty assignment.
     * Filters: active, not the current assignee, not on leave, not already assigned for that date in the same cycle.
     */
    @GetMapping("/{assignmentId}/available-personnel")
    public ResponseEntity<?> getAvailablePersonnel(
            @PathVariable Long assignmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CycleDutyAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return ResponseEntity.notFound().build();
        }

        Long cycleId = assignment.getCycleId();
        LocalDate date = assignment.getDate();
        Long currentPersonId = assignment.getPersonId();

        // Get all active personnel
        List<Personnel> allActive = personnelRepository.findByIsActiveTrue();

        // Get all person IDs already assigned for this date in this cycle
        List<CycleDutyAssignment> existingAssignments = assignmentRepository.findByCycleIdAndDate(cycleId, date);
        Set<Long> alreadyAssignedIds = existingAssignments.stream()
                .map(CycleDutyAssignment::getPersonId)
                .collect(Collectors.toSet());

        // Filter personnel
        List<Personnel> filteredList = allActive.stream()
                .filter(p -> !p.getId().equals(currentPersonId))
                .filter(p -> !alreadyAssignedIds.contains(p.getId()))
                .filter(p -> !leaveConflictDetector.isOnLeave(p.getId(), date))
                .filter(p -> {
                    if (search == null || search.isBlank()) return true;
                    String q = search.toLowerCase();
                    String name = p.getPersonName() != null ? p.getPersonName().toLowerCase() : "";
                    String badge = p.getBadgeId() != null ? p.getBadgeId().toLowerCase() : "";
                    return name.contains(q) || badge.contains(q);
                })
                .collect(Collectors.toList());

        long totalCount = filteredList.size();
        List<Map<String, Object>> paged = filteredList.stream()
                .skip((long) page * size)
                .limit(size)
                .map(p -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", p.getId());
                    dto.put("personName", p.getPersonName());
                    dto.put("badgeId", p.getBadgeId());
                    dto.put("section", p.getSection());
                    dto.put("currentDutyType", p.getDutyType() != null ? p.getDutyType() : "");
                    return dto;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", paged);
        response.put("totalElements", totalCount);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (int) Math.ceil((double) totalCount / size));
        return ResponseEntity.ok(response);
    }

    /**
     * Auto-reassigns a duty assignment to the lowest-disruption person from sections C-G.
     * The chosen person must not be on leave and not already assigned for that date.
     * Lowest disruption = fewest existing assignments in the cycle for that date.
     */
    @PostMapping("/{assignmentId}/auto-reassign")
    public ResponseEntity<?> autoReassign(@PathVariable Long assignmentId) {

        CycleDutyAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return ResponseEntity.notFound().build();
        }

        Long cycleId = assignment.getCycleId();
        LocalDate date = assignment.getDate();
        Long oldPersonId = assignment.getPersonId();

        // Get all person IDs already assigned for this date in this cycle
        List<CycleDutyAssignment> existingAssignments = assignmentRepository.findByCycleIdAndDate(cycleId, date);
        Set<Long> alreadyAssignedIds = existingAssignments.stream()
                .map(CycleDutyAssignment::getPersonId)
                .collect(Collectors.toSet());

        // Get all assignments in this cycle (for disruption scoring)
        List<CycleDutyAssignment> allCycleAssignments = assignmentRepository.findByCycleId(cycleId);

        // Count assignments per person in the cycle
        Map<Long, Long> assignmentCountByPerson = allCycleAssignments.stream()
                .collect(Collectors.groupingBy(CycleDutyAssignment::getPersonId, Collectors.counting()));

        // Excluded sections for primary filter
        Set<String> excludedSections = Set.of("A", "B");

        // Get active personnel from sections C, D, E, F, G (exclude A and B)
        List<Personnel> candidates = personnelRepository.findByIsActiveTrue().stream()
                .filter(p -> p.getSection() != null && !excludedSections.contains(p.getSection().toUpperCase()))
                .filter(p -> !p.getId().equals(oldPersonId))
                .filter(p -> !alreadyAssignedIds.contains(p.getId()))
                .filter(p -> !leaveConflictDetector.isOnLeave(p.getId(), date))
                .collect(Collectors.toList());

        // Fallback: if no candidates from sections C-G, try any active person
        if (candidates.isEmpty()) {
            candidates = personnelRepository.findByIsActiveTrue().stream()
                    .filter(p -> !p.getId().equals(oldPersonId))
                    .filter(p -> !alreadyAssignedIds.contains(p.getId()))
                    .filter(p -> !leaveConflictDetector.isOnLeave(p.getId(), date))
                    .collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No available personnel for auto-reassignment"));
        }

        // Pick the lowest disruption person (fewest existing assignments in cycle)
        Personnel chosen = candidates.stream()
                .min(Comparator.comparingLong(p -> assignmentCountByPerson.getOrDefault(p.getId(), 0L)))
                .orElse(candidates.get(0));

        // Update the assignment
        assignment.setPersonId(chosen.getId());
        assignment.setIsOverride(true);
        CycleDutyAssignment saved = assignmentRepository.save(assignment);

        // Audit log
        cycleAuditService.log(saved.getCycleId(), "DUTY_REASSIGNED",
                "Auto-reassigned duty from Person " + oldPersonId + " to " + chosen.getPersonName()
                        + " (ID: " + chosen.getId() + ") on " + saved.getDate() + " - auto-assigned due to leave",
                String.valueOf(oldPersonId), String.valueOf(chosen.getId()));

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("assignmentId", saved.getId());
        response.put("cycleId", saved.getCycleId());
        response.put("date", saved.getDate());
        response.put("platoonId", saved.getPlatoonId());
        response.put("sectionId", saved.getSectionId());
        response.put("newPersonId", chosen.getId());
        response.put("newPersonName", chosen.getPersonName());
        response.put("newPersonBadgeId", chosen.getBadgeId());
        response.put("newPersonPlatoonId", chosen.getPlatoonId());
        response.put("isOverride", true);
        response.put("reason", "auto-assigned due to leave");

        return ResponseEntity.ok(response);
    }
}
