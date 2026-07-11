package com.policescheduler.report.dto;

import java.util.List;

public record DutyCategoryRow(
    int serialNumber,
    String categoryName,
    List<PersonnelEntry> personnel,
    int[] designationCounts,
    List<SubDutyRow> subRows
) {}
