package com.policescheduler.dto;

/**
 * Response DTO for bulk reassignment results.
 * Contains counts of updated records across all affected tables.
 */
public record ReassignmentResultDto(
    int dutyTypesUpdated,
    int dutyAssignmentsUpdated,
    int personnelUpdated,
    String sourceSection,
    String targetSection
) {}
