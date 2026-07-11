package com.policescheduler.controller;

import com.policescheduler.dto.DutyTypeCreateDto;
import com.policescheduler.dto.DutyTypeDto;
import com.policescheduler.dto.DutyTypeUpdateDto;
import com.policescheduler.entity.DutyType;
import com.policescheduler.service.DutyTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/duty-types")
public class DutyTypeController {

    private final DutyTypeService dutyTypeService;

    public DutyTypeController(DutyTypeService dutyTypeService) {
        this.dutyTypeService = dutyTypeService;
    }

    /**
     * GET /api/duty-types — Returns all duty types ordered by section + sort_order.
     * Accepts optional ?section={code} query param to filter by section.
     */
    @GetMapping
    public ResponseEntity<List<DutyTypeDto>> listDutyTypes(
            @RequestParam(required = false) String section) {
        List<DutyType> dutyTypes = dutyTypeService.findAll(section);
        List<DutyTypeDto> dtos = dutyTypes.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /api/duty-types — Creates a new duty type. Returns 201 on success.
     */
    @PostMapping
    public ResponseEntity<DutyTypeDto> createDutyType(@Valid @RequestBody DutyTypeCreateDto dto) {
        DutyType created = dutyTypeService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    /**
     * PUT /api/duty-types/{id} — Updates an existing duty type. Returns 200 on success.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DutyTypeDto> updateDutyType(@PathVariable Long id,
                                                      @Valid @RequestBody DutyTypeUpdateDto dto) {
        DutyType updated = dutyTypeService.update(id, dto);
        return ResponseEntity.ok(toDto(updated));
    }

    /**
     * DELETE /api/duty-types/{id} — Deletes a duty type. Returns 204 on success.
     * Returns 404 if not found, 409 if active assignments exist.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDutyType(@PathVariable Long id) {
        dutyTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Maps a DutyType entity to DutyTypeDto for responses.
     */
    private DutyTypeDto toDto(DutyType dutyType) {
        return new DutyTypeDto(
                dutyType.getId(),
                dutyType.getName(),
                dutyType.getSection(),
                dutyType.getSortOrder(),
                dutyType.getLatitude(),
                dutyType.getLongitude(),
                dutyType.getRadiusMeters()
        );
    }
}
