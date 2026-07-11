package com.policescheduler.controller;

import com.policescheduler.dto.*;
import com.policescheduler.service.PersonnelService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {

    private final PersonnelService personnelService;

    public PersonnelController(PersonnelService personnelService) {
        this.personnelService = personnelService;
    }

    @GetMapping
    public ResponseEntity<Page<PersonnelDto>> listPersonnel(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String dutyType,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20) Pageable pageable) {

        PersonnelFilter filter = PersonnelFilter.builder()
                .search(search)
                .section(section)
                .dutyType(dutyType)
                .designation(designation)
                .isActive(isActive)
                .build();

        return ResponseEntity.ok(personnelService.listPersonnel(filter, pageable));
    }

    @GetMapping("/{badgeId}")
    public ResponseEntity<PersonnelDto> getByBadgeId(@PathVariable String badgeId) {
        return ResponseEntity.ok(personnelService.getByBadgeId(badgeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HIGHER_OFFICER')")
    public ResponseEntity<PersonnelDto> create(@Valid @RequestBody CreatePersonnelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(personnelService.create(request));
    }

    @PutMapping("/{badgeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HIGHER_OFFICER')")
    public ResponseEntity<PersonnelDto> update(@PathVariable String badgeId,
                                                @Valid @RequestBody UpdatePersonnelRequest request) {
        return ResponseEntity.ok(personnelService.update(badgeId, request));
    }

    @DeleteMapping("/{badgeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String badgeId) {
        personnelService.delete(badgeId);
        return ResponseEntity.noContent().build();
    }
}
