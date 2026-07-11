package com.policescheduler.service;

import com.policescheduler.dto.CycleCreateRequest;
import com.policescheduler.dto.CycleResponse;
import com.policescheduler.dto.CycleUpdateRequest;
import com.policescheduler.dto.PlatoonSectionMapping;
import com.policescheduler.entity.CyclePlatoonSection;
import com.policescheduler.entity.ScheduleCycle;
import com.policescheduler.exception.ResourceNotFoundException;
import com.policescheduler.repository.CycleDutyAssignmentRepository;
import com.policescheduler.repository.CyclePlatoonSectionRepository;
import com.policescheduler.repository.ScheduleCycleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core business logic service for platoon cycle management.
 * Orchestrates cycle creation, retrieval, update, and soft-delete operations.
 */
@Service
public class CycleService {

    private static final Logger log = LoggerFactory.getLogger(CycleService.class);

    private final ScheduleCycleRepository scheduleCycleRepository;
    private final CyclePlatoonSectionRepository cyclePlatoonSectionRepository;
    private final CycleDutyAssignmentRepository cycleDutyAssignmentRepository;
    private final CycleValidator cycleValidator;
    private final AssignmentGenerationService assignmentGenerationService;
    private final CycleAuditService cycleAuditService;

    public CycleService(ScheduleCycleRepository scheduleCycleRepository,
                        CyclePlatoonSectionRepository cyclePlatoonSectionRepository,
                        CycleDutyAssignmentRepository cycleDutyAssignmentRepository,
                        CycleValidator cycleValidator,
                        AssignmentGenerationService assignmentGenerationService,
                        CycleAuditService cycleAuditService) {
        this.scheduleCycleRepository = scheduleCycleRepository;
        this.cyclePlatoonSectionRepository = cyclePlatoonSectionRepository;
        this.cycleDutyAssignmentRepository = cycleDutyAssignmentRepository;
        this.cycleValidator = cycleValidator;
        this.assignmentGenerationService = assignmentGenerationService;
        this.cycleAuditService = cycleAuditService;
    }

    /**
     * Creates a new platoon rotation cycle with platoon-section mappings and
     * auto-generated duty assignments.
     */
    @Transactional
    public CycleResponse createCycle(CycleCreateRequest request) {
        // 1. Validate and calculate end date
        LocalDate endDate = cycleValidator.validateAndCalculateEndDate(request);

        // 1b. Check for overlapping active cycles (HARD BLOCK)
        List<ScheduleCycle> overlapping = scheduleCycleRepository.findOverlappingActiveCycles(
                request.startDate(), endDate);
        if (!overlapping.isEmpty()) {
            ScheduleCycle existing = overlapping.get(0);
            throw new IllegalStateException("Cycle dates overlap with existing cycle #" + existing.getId()
                    + " (" + existing.getStartDate() + " to " + existing.getEndDate()
                    + "). Please choose dates after " + existing.getEndDate() + ".");
        }

        // 2. Create and persist the cycle entity
        ScheduleCycle cycle = new ScheduleCycle();
        cycle.setStartDate(request.startDate());
        cycle.setEndDate(endDate);
        cycle.setRotationDays(request.rotationDays());
        cycle.setStatus("ACTIVE");

        ScheduleCycle savedCycle = scheduleCycleRepository.save(cycle);

        // 3. Create and persist platoon-section mappings
        List<CyclePlatoonSection> savedMappings = savePlatoonSectionMappings(
                savedCycle.getId(), request.platoonSections());

        // 4. Generate duty assignments
        assignmentGenerationService.generateAssignments(
                savedCycle.getId(), savedCycle.getStartDate(), savedCycle.getEndDate(), savedMappings);

        // 5. Audit log
        cycleAuditService.log(savedCycle.getId(), "CYCLE_CREATED",
                "Cycle created from " + savedCycle.getStartDate() + " to " + endDate + " (" + request.rotationDays() + " days)",
                null, null);

        // 6. Return response
        return buildCycleResponse(savedCycle, savedMappings);
    }

    /**
     * Returns cycles ordered by start_date DESC with optional status filter.
     * If statusFilter is null, returns all non-DELETED cycles.
     */
    public List<CycleResponse> getCycles(String statusFilter) {
        List<ScheduleCycle> cycles;

        if (statusFilter != null && !statusFilter.isBlank()) {
            cycles = scheduleCycleRepository.findByStatusOrderByStartDateDesc(statusFilter);
        } else {
            cycles = scheduleCycleRepository.findByStatusNotOrderByStartDateDesc("DELETED");
        }

        return cycles.stream()
                .map(cycle -> {
                    List<CyclePlatoonSection> mappings = cyclePlatoonSectionRepository
                            .findByCycleId(cycle.getId());
                    return buildCycleResponse(cycle, mappings);
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns a cycle by ID with its mappings and assignments.
     * Throws ResourceNotFoundException if the cycle does not exist.
     */
    public CycleResponse getCycleById(Long id) {
        ScheduleCycle cycle = scheduleCycleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle", id));

        List<CyclePlatoonSection> mappings = cyclePlatoonSectionRepository.findByCycleId(id);
        return buildCycleResponse(cycle, mappings);
    }

    /**
     * Partially updates a cycle, modifying only non-null fields from the request.
     * If platoon-section mappings are provided, old mappings and assignments are deleted
     * and regenerated.
     */
    @Transactional
    public CycleResponse partialUpdateCycle(Long id, CycleUpdateRequest request) {
        ScheduleCycle cycle = scheduleCycleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle", id));

        // Capture old values for audit
        String oldValues = "startDate=" + cycle.getStartDate() + ", endDate=" + cycle.getEndDate()
                + ", rotationDays=" + cycle.getRotationDays() + ", status=" + cycle.getStatus();

        boolean datesChanged = false;

        // Update only non-null fields
        if (request.startDate() != null) {
            cycle.setStartDate(request.startDate());
            datesChanged = true;
        }
        if (request.rotationDays() != null) {
            cycle.setRotationDays(request.rotationDays());
            datesChanged = true;
        }
        if (request.status() != null) {
            cycle.setStatus(request.status());
        }

        // Recalculate end date if start date or rotation days changed
        if (datesChanged) {
            LocalDate endDate = cycle.getStartDate().plusDays(cycle.getRotationDays() - 1);
            cycle.setEndDate(endDate);
        }

        ScheduleCycle savedCycle = scheduleCycleRepository.save(cycle);

        // Handle platoon-section mapping changes
        List<CyclePlatoonSection> mappings;
        if (request.platoonSections() != null) {
            // Delete old mappings and assignments, then regenerate
            cyclePlatoonSectionRepository.deleteByCycleId(id);
            cycleDutyAssignmentRepository.deleteByCycleId(id);

            mappings = savePlatoonSectionMappings(id, request.platoonSections());

            assignmentGenerationService.generateAssignments(
                    id, savedCycle.getStartDate(), savedCycle.getEndDate(), mappings);
        } else if (datesChanged) {
            // Dates changed but mappings didn't — regenerate assignments with existing mappings
            cycleDutyAssignmentRepository.deleteByCycleId(id);
            mappings = cyclePlatoonSectionRepository.findByCycleId(id);
            assignmentGenerationService.generateAssignments(
                    id, savedCycle.getStartDate(), savedCycle.getEndDate(), mappings);
        } else {
            mappings = cyclePlatoonSectionRepository.findByCycleId(id);
        }

        // Audit log
        String newValues = "startDate=" + savedCycle.getStartDate() + ", endDate=" + savedCycle.getEndDate()
                + ", rotationDays=" + savedCycle.getRotationDays() + ", status=" + savedCycle.getStatus();
        cycleAuditService.log(id, "CYCLE_UPDATED",
                "Cycle partially updated", oldValues, newValues);

        return buildCycleResponse(savedCycle, mappings);
    }

    /**
     * Fully replaces a cycle's fields, re-validates, and regenerates all mappings
     * and assignments.
     */
    @Transactional
    public CycleResponse fullUpdateCycle(Long id, CycleCreateRequest request) {
        ScheduleCycle cycle = scheduleCycleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle", id));

        // Capture old values for audit
        String oldValues = "startDate=" + cycle.getStartDate() + ", endDate=" + cycle.getEndDate()
                + ", rotationDays=" + cycle.getRotationDays();

        // Re-validate and calculate end date
        LocalDate endDate = cycleValidator.validateAndCalculateEndDate(request);

        // Replace all fields
        cycle.setStartDate(request.startDate());
        cycle.setEndDate(endDate);
        cycle.setRotationDays(request.rotationDays());

        ScheduleCycle savedCycle = scheduleCycleRepository.save(cycle);

        // Delete old mappings and assignments
        cyclePlatoonSectionRepository.deleteByCycleId(id);
        cycleDutyAssignmentRepository.deleteByCycleId(id);

        // Save new mappings and regenerate assignments
        List<CyclePlatoonSection> mappings = savePlatoonSectionMappings(id, request.platoonSections());
        assignmentGenerationService.generateAssignments(
                id, savedCycle.getStartDate(), savedCycle.getEndDate(), mappings);

        // Audit log
        String newValues = "startDate=" + savedCycle.getStartDate() + ", endDate=" + savedCycle.getEndDate()
                + ", rotationDays=" + savedCycle.getRotationDays();
        cycleAuditService.log(id, "CYCLE_UPDATED",
                "Cycle fully updated", oldValues, newValues);

        return buildCycleResponse(savedCycle, mappings);
    }

    /**
     * Returns the next available start date (day after the latest ACTIVE cycle ends).
     * If no active cycles exist, returns today's date.
     */
    public LocalDate getNextAvailableDate() {
        List<ScheduleCycle> activeCycles = scheduleCycleRepository.findActiveCyclesOrderByEndDateDesc();
        if (activeCycles.isEmpty()) {
            return LocalDate.now();
        }
        return activeCycles.get(0).getEndDate().plusDays(1);
    }

    /**
     * Soft-deletes a cycle by setting its status to DELETED.
     */
    @Transactional
    public void deleteCycle(Long id) {
        ScheduleCycle cycle = scheduleCycleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle", id));

        cycle.setStatus("DELETED");
        scheduleCycleRepository.save(cycle);

        // Audit log
        cycleAuditService.log(id, "CYCLE_DELETED",
                "Cycle deleted (soft-delete) - was from " + cycle.getStartDate() + " to " + cycle.getEndDate(),
                null, null);
    }

    /**
     * Persists platoon-section mappings for a cycle based on the request DTOs.
     */
    private List<CyclePlatoonSection> savePlatoonSectionMappings(Long cycleId,
                                                                  List<PlatoonSectionMapping> platoonSections) {
        List<CyclePlatoonSection> entities = new ArrayList<>();

        for (PlatoonSectionMapping mapping : platoonSections) {
            for (Long sectionId : mapping.sectionIds()) {
                CyclePlatoonSection entity = new CyclePlatoonSection();
                entity.setCycleId(cycleId);
                entity.setPlatoonId(mapping.platoonId());
                entity.setSectionId(sectionId);
                entities.add(entity);
            }
        }

        return cyclePlatoonSectionRepository.saveAll(entities);
    }

    /**
     * Builds a CycleResponse DTO from a ScheduleCycle entity and its platoon-section mappings.
     * Groups CyclePlatoonSection entities by platoonId to construct PlatoonSectionMapping DTOs.
     */
    private CycleResponse buildCycleResponse(ScheduleCycle cycle, List<CyclePlatoonSection> mappings) {
        // Group by platoonId to build PlatoonSectionMapping DTOs
        Map<Long, List<Long>> grouped = mappings.stream()
                .collect(Collectors.groupingBy(
                        CyclePlatoonSection::getPlatoonId,
                        Collectors.mapping(CyclePlatoonSection::getSectionId, Collectors.toList())
                ));

        List<PlatoonSectionMapping> platoonSectionMappings = grouped.entrySet().stream()
                .map(entry -> new PlatoonSectionMapping(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return new CycleResponse(
                cycle.getId(),
                cycle.getStartDate(),
                cycle.getEndDate(),
                cycle.getRotationDays(),
                cycle.getStatus(),
                platoonSectionMappings,
                cycle.getCreatedAt(),
                cycle.getUpdatedAt()
        );
    }
}
