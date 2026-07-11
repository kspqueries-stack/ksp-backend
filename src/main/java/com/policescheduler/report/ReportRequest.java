package com.policescheduler.report;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ReportRequest {
    private String reportType;
    private LocalDate date;
    private String section;
    private String designation;
    private String dutyType;
    private Boolean isActive;
    private String leaveStatus;
    private String leaveType;
    @Builder.Default
    private String locale = "en";
    private boolean combined;
}
