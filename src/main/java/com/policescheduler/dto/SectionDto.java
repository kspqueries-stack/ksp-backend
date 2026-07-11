package com.policescheduler.dto;

/**
 * Response DTO for section data.
 * Includes the duty type count for display in the section management dialog.
 */
public record SectionDto(
    Long id,
    String code,
    String name,
    String description,
    Integer sortOrder,
    Boolean isActive,
    Integer dutyTypeCount
) {}
