package com.policescheduler.report;

public interface PdfReportGenerator {

    /**
     * Generates a PDF report as a byte array.
     * @param request contains filters, locale, date, and report-specific parameters
     * @return PDF bytes ready for HTTP response or file storage
     */
    byte[] generate(ReportRequest request);

    /**
     * Returns the report type identifier, e.g., "platoon_chart", "form_168".
     */
    String getReportType();
}
