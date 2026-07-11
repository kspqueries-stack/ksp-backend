package com.policescheduler.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for partial (PATCH) updates to an existing cycle.
 * All fields are optional — only provided fields will be updated.
 */
public record CycleUpdateRequest(
    LocalDate startDate,

    @Min(value = 1, message = "Rotation days must be at least 1")
    Integer rotationDays,

    @Size(min = 5, max = 5, message = "Exactly 5 platoon-section mappings are required")
    @Valid
    List<PlatoonSectionMapping> platoonSections,

    String status
) {}
