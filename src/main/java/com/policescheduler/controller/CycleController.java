package com.policescheduler.controller;

import com.policescheduler.dto.CycleAuditLogResponse;
import com.policescheduler.dto.CycleCreateRequest;
import com.policescheduler.dto.CycleResponse;
import com.policescheduler.dto.CycleUpdateRequest;
import com.policescheduler.entity.CycleAuditLog;
import com.policescheduler.entity.CyclePlatoonSection;
import com.policescheduler.entity.ScheduleCycle;
import com.policescheduler.repository.CyclePlatoonSectionRepository;
import com.policescheduler.repository.ScheduleCycleRepository;
import com.policescheduler.service.CycleAuditService;
import com.policescheduler.service.CycleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for platoon cycle management operations.
 * Handles CRUD operations for rotation cycles including creation,
 * retrieval, partial/full updates, and soft-delete.
 */
@RestController
@RequestMapping("/api/cycles")
public class CycleController {

    private final CycleService cycleService;
    private final CycleAuditService cycleAuditService;
    private final ScheduleCycleRepository scheduleCycleRepository;
    private final CyclePlatoonSectionRepository cyclePlatoonSectionRepository;

    public CycleController(CycleService cycleService, CycleAuditService cycleAuditService,
                           ScheduleCycleRepository scheduleCycleRepository,
                           CyclePlatoonSectionRepository cyclePlatoonSectionRepository) {
        this.cycleService = cycleService;
        this.cycleAuditService = cycleAuditService;
        this.scheduleCycleRepository = scheduleCycleRepository;
        this.cyclePlatoonSectionRepository = cyclePlatoonSectionRepository;
    }

    /**
     * Creates a new platoon rotation cycle.
     *
     * @param request the cycle creation request with start date, rotation days, and platoon-section mappings
     * @return the created cycle with HTTP 201 status
     */
    @PostMapping
    public ResponseEntity<CycleResponse> createCycle(@Valid @RequestBody CycleCreateRequest request) {
        CycleResponse response = cycleService.createCycle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists cycles with optional status filter.
     * If no status is provided, returns all non-deleted cycles ordered by start_date DESC.
     *
     * @param status optional status filter (ACTIVE, COMPLETED, DELETED)
     * @return list of cycles matching the filter criteria
     */
    @GetMapping
    public ResponseEntity<List<CycleResponse>> getCycles(
            @RequestParam(required = false) String status) {
        List<CycleResponse> cycles = cycleService.getCycles(status);
        return ResponseEntity.ok(cycles);
    }

    /**
     * Retrieves a cycle by its ID, including platoon-section mappings.
     *
     * @param id the cycle ID
     * @return the cycle details, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<CycleResponse> getCycleById(@PathVariable Long id) {
        CycleResponse response = cycleService.getCycleById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Partially updates a cycle. Only provided (non-null) fields are modified.
     * If platoon-section mappings are changed, duty assignments are regenerated.
     *
     * @param id the cycle ID
     * @param request the partial update request
     * @return the updated cycle, or 404 if not found
     */
    @PatchMapping("/{id}")
    public ResponseEntity<CycleResponse> partialUpdateCycle(
            @PathVariable Long id,
            @Valid @RequestBody CycleUpdateRequest request) {
        CycleResponse response = cycleService.partialUpdateCycle(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Fully replaces a cycle's fields, re-validates, and regenerates all
     * mappings and assignments.
     *
     * @param id the cycle ID
     * @param request the full replacement request
     * @return the updated cycle, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<CycleResponse> fullUpdateCycle(
            @PathVariable Long id,
            @Valid @RequestBody CycleCreateRequest request) {
        CycleResponse response = cycleService.fullUpdateCycle(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Soft-deletes a cycle by setting its status to DELETED.
     * Associated mappings and assignments are preserved for audit purposes.
     *
     * @param id the cycle ID
     * @return HTTP 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCycle(@PathVariable Long id) {
        cycleService.deleteCycle(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the next available start date for a new cycle.
     * This is the day after the latest ACTIVE cycle ends.
     * If no active cycles exist, returns today's date.
     */
    @GetMapping("/next-available-date")
    public ResponseEntity<?> getNextAvailableDate() {
        java.time.LocalDate nextDate = cycleService.getNextAvailableDate();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nextAvailableDate", nextDate.toString());
        return ResponseEntity.ok(result);
    }

    /**
     * Auto-generates the next cycle's platoon-section mappings based on circular rotation pattern.
     * Sections C(id:3), D(id:4), E(id:5), F(id:6), G(id:7) rotate among 5 platoons.
     */
    @GetMapping("/auto-generate")
    public ResponseEntity<?> autoGenerateMappings() {
        // Section IDs in rotation order
        List<Long> sectionIds = List.of(3L, 4L, 5L, 6L, 7L);
        // Platoon IDs in order
        List<Long> platoonIds = List.of(1L, 2L, 3L, 4L, 5L);

        // Get the most recent non-deleted cycle
        List<ScheduleCycle> allCycles = scheduleCycleRepository.findByStatusNotOrderByStartDateDesc("DELETED");

        Map<String, Object> response = new LinkedHashMap<>();

        if (allCycles.isEmpty()) {
            // No previous cycles — use default first rotation: P1→3, P2→4, P3→5, P4→6, P5→7
            List<Map<String, Object>> mappings = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("platoonId", platoonIds.get(i));
                m.put("sectionIds", List.of(sectionIds.get(i)));
                mappings.add(m);
            }
            response.put("mappings", mappings);
            response.put("previousCycle", null);
            response.put("rotationIndex", 0);
            return ResponseEntity.ok(response);
        }

        // Get the most recent cycle's mappings
        ScheduleCycle lastCycle = allCycles.get(0);
        List<CyclePlatoonSection> lastMappings = cyclePlatoonSectionRepository.findByCycleId(lastCycle.getId());

        // Build a map of platoonId -> sectionId from the last cycle
        Map<Long, Long> lastPlatoonToSection = new LinkedHashMap<>();
        for (CyclePlatoonSection mapping : lastMappings) {
            lastPlatoonToSection.put(mapping.getPlatoonId(), mapping.getSectionId());
        }

        // Generate new mappings: each platoon gets the NEXT section in circular order
        // This guarantees no platoon gets the same section consecutively
        List<Map<String, Object>> mappings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Long platoonId = platoonIds.get(i);
            Long lastSection = lastPlatoonToSection.getOrDefault(platoonId, sectionIds.get(i));
            int lastIndex = sectionIds.indexOf(lastSection);
            if (lastIndex < 0) lastIndex = i;
            // Shift by 1 position for this platoon
            int nextIndex = (lastIndex + 1) % 5;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("platoonId", platoonId);
            m.put("sectionIds", List.of(sectionIds.get(nextIndex)));
            mappings.add(m);
        }

        // Build previous cycle info
        Map<String, Object> previousCycleInfo = new LinkedHashMap<>();
        previousCycleInfo.put("id", lastCycle.getId());
        List<Map<String, Object>> prevMappings = lastMappings.stream()
                .map(lm -> {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("platoonId", lm.getPlatoonId());
                    pm.put("sectionIds", List.of(lm.getSectionId()));
                    return pm;
                })
                .collect(Collectors.toList());
        previousCycleInfo.put("mappings", prevMappings);

        response.put("mappings", mappings);
        response.put("previousCycle", previousCycleInfo);
        response.put("previousPlatoonSections", buildPreviousPlatoonSectionsMap(lastMappings));
        response.put("rotationIndex", 0);

        return ResponseEntity.ok(response);
    }

    /**
     * Builds a map of platoonId -> sectionId from the previous cycle's mappings.
     */
    private Map<Long, Long> buildPreviousPlatoonSectionsMap(List<CyclePlatoonSection> mappings) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (CyclePlatoonSection m : mappings) {
            result.put(m.getPlatoonId(), m.getSectionId());
        }
        return result;
    }

    /**
     * Returns all audit logs ordered by creation date descending (for the activities page).
     */
    @GetMapping("/audit")
    public ResponseEntity<List<CycleAuditLogResponse>> getAllAuditLogs() {
        List<CycleAuditLogResponse> response = cycleAuditService.getAllAuditLogs().stream()
                .map(this::toAuditResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Returns audit logs for a specific cycle ordered by creation date descending.
     */
    @GetMapping("/{id}/audit")
    public ResponseEntity<List<CycleAuditLogResponse>> getAuditLogsByCycle(@PathVariable Long id) {
        List<CycleAuditLogResponse> response = cycleAuditService.getAuditLogByCycle(id).stream()
                .map(this::toAuditResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    private CycleAuditLogResponse toAuditResponse(CycleAuditLog log) {
        return new CycleAuditLogResponse(
                log.getId(),
                log.getCycleId(),
                log.getAction(),
                log.getDescription(),
                log.getOldValue(),
                log.getNewValue(),
                log.getPerformedBy(),
                log.getCreatedAt()
        );
    }
}
