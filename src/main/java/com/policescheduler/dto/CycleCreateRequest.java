package com.policescheduler.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating a new platoon rotation cycle.
 */
public record CycleCreateRequest(
    @NotNull(message = "Start date is required")
    LocalDate startDate,

    @NotNull(message = "Rotation days is required")
    @Min(value = 1, message = "Rotation days must be at least 1")
    Integer rotationDays,

    @NotNull(message = "Platoon section mappings are required")
    @Size(min = 5, max = 5, message = "Exactly 5 platoon-section mappings are required")
    @Valid
    List<PlatoonSectionMapping> platoonSections
) {}
