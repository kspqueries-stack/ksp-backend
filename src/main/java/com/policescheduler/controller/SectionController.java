package com.policescheduler.controller;

import com.policescheduler.dto.ReassignmentRequestDto;
import com.policescheduler.dto.ReassignmentResultDto;
import com.policescheduler.dto.SectionCreateDto;
import com.policescheduler.dto.SectionDto;
import com.policescheduler.dto.SectionUpdateDto;
import com.policescheduler.entity.Section;
import com.policescheduler.service.ReassignmentService;
import com.policescheduler.service.SectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sections")
public class SectionController {

    private final SectionService sectionService;
    private final ReassignmentService reassignmentService;

    public SectionController(SectionService sectionService, ReassignmentService reassignmentService) {
        this.sectionService = sectionService;
        this.reassignmentService = reassignmentService;
    }

    /**
     * GET /api/sections — Returns all sections (active + inactive) with duty type counts.
     */
    @GetMapping
    public ResponseEntity<List<SectionDto>> listAllSections() {
        List<Section> sections = sectionService.findAll();
        List<SectionDto> dtos = sections.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/sections/active — Returns active sections only (for tab rendering).
     */
    @GetMapping("/active")
    public ResponseEntity<List<SectionDto>> listActiveSections() {
        List<Section> sections = sectionService.findActive();
        List<SectionDto> dtos = sections.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /api/sections — Creates a new section. Returns 201 on success, 409 on duplicate code.
     */
    @PostMapping
    public ResponseEntity<SectionDto> createSection(@Valid @RequestBody SectionCreateDto dto) {
        Section created = sectionService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    /**
     * PUT /api/sections/{id} — Updates an existing section. Returns 200 on success.
     */
    @PutMapping("/{id}")
    public ResponseEntity<SectionDto> updateSection(@PathVariable Long id,
                                                    @Valid @RequestBody SectionUpdateDto dto) {
        Section updated = sectionService.update(id, dto);
        return ResponseEntity.ok(toDto(updated));
    }

    /**
     * POST /api/sections/reassign — Bulk reassignment of duty types to a different section.
     * Delegates to ReassignmentService for transactional cascade updates.
     */
    @PostMapping("/reassign")
    public ResponseEntity<ReassignmentResultDto> reassignDutyTypes(
            @Valid @RequestBody ReassignmentRequestDto dto) {
        ReassignmentResultDto result = reassignmentService.reassign(dto);
        return ResponseEntity.ok(result);
    }

    /**
     * Maps a Section entity to SectionDto, including the duty type count.
     */
    private SectionDto toDto(Section section) {
        int dutyTypeCount = sectionService.countDutyTypes(section.getCode());
        return new SectionDto(
                section.getId(),
                section.getCode(),
                section.getName(),
                section.getDescription(),
                section.getSortOrder(),
                section.getIsActive(),
                dutyTypeCount
        );
    }
}
