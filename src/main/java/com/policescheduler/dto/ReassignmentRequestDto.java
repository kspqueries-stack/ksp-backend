package com.policescheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for bulk reassignment of duty types to a different section.
 */
public record ReassignmentRequestDto(
    @NotEmpty(message = "At least one duty type ID is required")
    @Size(max = 50, message = "Cannot reassign more than 50 duty types at once")
    List<Long> dutyTypeIds,

    @NotBlank(message = "Target section code is required")
    String targetSection
) {}
