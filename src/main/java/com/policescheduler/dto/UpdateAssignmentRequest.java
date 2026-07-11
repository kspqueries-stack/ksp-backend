package com.policescheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssignmentRequest {

    private Long personnelId;
    private Long dutyTypeId;
    private String section;
    private LocalDate effectiveDate;
    private LocalDate endDate;
    private String shift;
    private String subAssignment;
    private Boolean isCurrent;
}
