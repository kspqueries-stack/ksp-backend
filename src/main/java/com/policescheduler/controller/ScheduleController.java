package com.policescheduler.controller;

import com.policescheduler.dto.*;
import com.policescheduler.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/sections")
    public ResponseEntity<SectionOverview> getSectionsOverview() {
        return ResponseEntity.ok(scheduleService.getSectionsOverview());
    }

    @GetMapping("/section-a")
    public ResponseEntity<List<DutyAssignmentDto>> getSectionA() {
        return ResponseEntity.ok(scheduleService.getSectionA());
    }

    @GetMapping("/section-b")
    public ResponseEntity<List<DutyAssignmentDto>> getSectionB() {
        return ResponseEntity.ok(scheduleService.getSectionB());
    }

    @GetMapping("/section-c")
    public ResponseEntity<SectionCData> getSectionC() {
        return ResponseEntity.ok(scheduleService.getSectionC());
    }

    @GetMapping("/platoons")
    public ResponseEntity<PlatoonRotationDto> getPlatoonRotation() {
        return ResponseEntity.ok(scheduleService.getPlatoonRotation());
    }

    @PostMapping("/platoons/rotate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlatoonRotationDto> rotatePlatoons() {
        return ResponseEntity.ok(scheduleService.rotatePlatoons());
    }

    @PutMapping("/assignments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HIGHER_OFFICER')")
    public ResponseEntity<DutyAssignmentDto> updateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssignmentRequest request) {
        return ResponseEntity.ok(scheduleService.updateAssignment(id, request));
    }
}
