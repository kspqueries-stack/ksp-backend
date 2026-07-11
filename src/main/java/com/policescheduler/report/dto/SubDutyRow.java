package com.policescheduler.report.dto;

import java.util.List;

public record SubDutyRow(String subCategoryName, List<PersonnelEntry> personnel, int[] designationCounts) {}
