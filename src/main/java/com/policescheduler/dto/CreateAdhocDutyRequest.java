package com.policescheduler.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateAdhocDutyRequest(
    @NotBlank String dutyName,
    @NotNull LocalDate date,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull @Min(1) Integer requiredCount,
    String location,
    String pickFrom,
    List<Long> platoonIds,
    List<Long> personnelIds
) {}
