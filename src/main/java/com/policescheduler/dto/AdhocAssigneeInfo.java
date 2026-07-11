package com.policescheduler.dto;

public record AdhocAssigneeInfo(
    Long personId,
    String personName,
    String badgeId,
    String designation,
    String section,
    String originalShift,
    String originalDutyName,
    Integer disruptionScore,
    String status
) {}
