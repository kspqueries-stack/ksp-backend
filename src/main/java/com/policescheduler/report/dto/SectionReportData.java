package com.policescheduler.report.dto;

import java.util.List;

public record SectionReportData(
    String sectionLabel,
    List<DutyCategoryRow> rows,
    int[] designationTotals
) {}
