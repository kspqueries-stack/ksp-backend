package com.policescheduler.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record AdhocDutyResponse(
    Long id,
    String dutyName,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    Integer requiredCount,
    Integer actualCount,
    String pickFrom,
    List<Long> platoonIds,
    String location,
    String status,
    LocalDateTime createdAt,
    List<AdhocAssigneeInfo> assignees
) {}
