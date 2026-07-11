package com.policescheduler.report;

public class ReportGenerationException extends RuntimeException {

    private final String reportType;

    public ReportGenerationException(String reportType, String message, Throwable cause) {
        super(message, cause);
        this.reportType = reportType;
    }

    public String getReportType() {
        return reportType;
    }
}
