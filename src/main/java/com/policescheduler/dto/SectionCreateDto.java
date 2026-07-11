package com.policescheduler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new section.
 * Code must be uppercase alphabetic, max 5 characters, and unique.
 */
public record SectionCreateDto(
    @NotBlank(message = "Section code is required")
    @Size(max = 5, message = "Section code must not exceed 5 characters")
    @Pattern(regexp = "^[A-Z]+$", message = "Section code must contain only uppercase letters")
    String code,

    @NotBlank(message = "Section name is required")
    @Size(max = 100, message = "Section name must not exceed 100 characters")
    String name,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description,

    @Min(value = 0, message = "Sort order must be at least 0")
    @Max(value = 999, message = "Sort order must not exceed 999")
    Integer sortOrder,

    Boolean isActive
) {}
