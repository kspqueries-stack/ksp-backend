package com.policescheduler.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for a completed duty reassignment, returning the updated assignment details.
 */
public record ReassignResponse(
    Long id,
    Long cycleId,
    LocalDate date,
    Long platoonId,
    Long sectionId,
    Long personId,
    String shiftType,
    Boolean isOverride,
    LocalDateTime updatedAt
) {}
