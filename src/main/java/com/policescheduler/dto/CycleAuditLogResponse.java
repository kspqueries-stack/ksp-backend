package com.policescheduler.dto;

import java.time.LocalDateTime;

public record CycleAuditLogResponse(
    Long id,
    Long cycleId,
    String action,
    String description,
    String oldValue,
    String newValue,
    Long performedBy,
    LocalDateTime createdAt
) {}
