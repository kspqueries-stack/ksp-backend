package com.policescheduler.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO containing full cycle details including platoon-section mappings.
 */
public record CycleResponse(
    Long id,
    LocalDate startDate,
    LocalDate endDate,
    Integer rotationDays,
    String status,
    List<PlatoonSectionMapping> platoonSections,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
