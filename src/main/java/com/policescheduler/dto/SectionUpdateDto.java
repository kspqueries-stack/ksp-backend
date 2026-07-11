package com.policescheduler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing section.
 * Code is not updatable, so it is not included here.
 */
public record SectionUpdateDto(
    @Size(max = 100, message = "Section name must not exceed 100 characters")
    String name,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description,

    @Min(value = 0, message = "Sort order must be at least 0")
    @Max(value = 999, message = "Sort order must not exceed 999")
    Integer sortOrder,

    Boolean isActive
) {}
