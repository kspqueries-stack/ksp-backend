package com.policescheduler.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing duty type.
 * All fields are optional (nullable) for partial updates.
 */
public record DutyTypeUpdateDto(
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 5, message = "Section code must not exceed 5 characters")
        String section,

        @Min(value = 1, message = "Sort order must be at least 1")
        @Max(value = 999, message = "Sort order must not exceed 999")
        Integer sortOrder,

        @DecimalMin(value = "-90", message = "Latitude must be at least -90")
        @DecimalMax(value = "90", message = "Latitude must not exceed 90")
        Double latitude,

        @DecimalMin(value = "-180", message = "Longitude must be at least -180")
        @DecimalMax(value = "180", message = "Longitude must not exceed 180")
        Double longitude,

        @Min(value = 1, message = "Radius must be at least 1 meter")
        @Max(value = 10000, message = "Radius must not exceed 10000 meters")
        Integer radiusMeters
) {}
