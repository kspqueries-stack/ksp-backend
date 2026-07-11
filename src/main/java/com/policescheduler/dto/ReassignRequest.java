package com.policescheduler.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for reassigning a duty assignment to a different person.
 */
public record ReassignRequest(
    @NotNull(message = "Assignment ID is required")
    Long assignmentId,

    @NotNull(message = "New person ID is required")
    Long newPersonId
) {}
