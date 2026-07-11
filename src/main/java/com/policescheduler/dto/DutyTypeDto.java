package com.policescheduler.dto;

/**
 * Response DTO representing a duty type with all fields.
 */
public record DutyTypeDto(
        Long id,
        String name,
        String section,
        Integer sortOrder,
        Double latitude,
        Double longitude,
        Integer radiusMeters
) {}
