package com.policescheduler.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Mapping of a platoon to one or more sections within a cycle.
 */
public record PlatoonSectionMapping(
    @NotNull(message = "Platoon ID is required")
    Long platoonId,

    @NotNull(message = "Section IDs are required")
    @Size(min = 1, message = "At least one section must be assigned to each platoon")
    List<Long> sectionIds
) {}
