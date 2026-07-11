package com.policescheduler.controller;

import com.policescheduler.report.PdfReportGenerator;
import com.policescheduler.report.ReportFileService;
import com.policescheduler.report.ReportRequest;
import com.policescheduler.report.S3ReportStorageService;
import com.policescheduler.report.generator.DailyStatementExcelGenerator;
import com.policescheduler.report.generator.LeaveStatementExcelGenerator;
import com.policescheduler.report.generator.MtSectionExcelGenerator;
import com.policescheduler.report.generator.PlatoonChartExcelGenerator;
import com.policescheduler.report.generator.SectionABExcelGenerator;
import com.policescheduler.repository.ReportHistoryRepository;
import net.jqwik.api.*;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ReportController platoon chart section validation.
 * Feature: platoon-chart-multi-section, Property 10: Invalid section values are rejected
 */
class ReportControllerPlatoonChartPropertyTest {

    private static final Set<String> VALID_SECTIONS = Set.of("C", "D", "E", "F", "G",
            "c", "d", "e", "f", "g");

    // --- Property 10: Invalid section values are rejected ---
    // Feature: platoon-chart-multi-section, Property 10: Invalid section values are rejected

    @Property(tries = 100)
    void invalidSection_returns400(
            @ForAll("invalidSections") String invalidSection) {

        PdfReportGenerator mockGenerator = mock(PdfReportGenerator.class);
        when(mockGenerator.getReportType()).thenReturn("platoon_chart");
        when(mockGenerator.generate(any(ReportRequest.class))).thenReturn(new byte[]{1, 2, 3});

        ReportFileService mockFileService = mock(ReportFileService.class);
        SectionABExcelGenerator mockExcelGen = mock(SectionABExcelGenerator.class);
        PlatoonChartExcelGenerator mockPlatoonExcelGen = mock(PlatoonChartExcelGenerator.class);
        MtSectionExcelGenerator mockMtExcelGen = mock(MtSectionExcelGenerator.class);
        DailyStatementExcelGenerator mockDailyExcelGen = mock(DailyStatementExcelGenerator.class);
        LeaveStatementExcelGenerator mockLeaveExcelGen = mock(LeaveStatementExcelGenerator.class);
        S3ReportStorageService mockS3 = mock(S3ReportStorageService.class);
        ReportHistoryRepository mockHistoryRepo = mock(ReportHistoryRepository.class);

        ReportController controller = new ReportController(List.of(mockGenerator), mockFileService, mockExcelGen, mockPlatoonExcelGen, mockMtExcelGen, mockDailyExcelGen, mockLeaveExcelGen, mockS3, mockHistoryRepo);

        ResponseEntity<byte[]> response = controller.generatePlatoonChart(null, invalidSection, "en");

        assertEquals(400, response.getStatusCode().value(),
                "Invalid section '" + invalidSection + "' should return 400");
    }

    @Property(tries = 100)
    void validSection_returns200(
            @ForAll("validSectionValues") String validSection) {

        PdfReportGenerator mockGenerator = mock(PdfReportGenerator.class);
        when(mockGenerator.getReportType()).thenReturn("platoon_chart");
        when(mockGenerator.generate(any(ReportRequest.class))).thenReturn(new byte[]{1, 2, 3});

        ReportFileService mockFileService = mock(ReportFileService.class);
        SectionABExcelGenerator mockExcelGen = mock(SectionABExcelGenerator.class);
        PlatoonChartExcelGenerator mockPlatoonExcelGen = mock(PlatoonChartExcelGenerator.class);
        MtSectionExcelGenerator mockMtExcelGen = mock(MtSectionExcelGenerator.class);
        DailyStatementExcelGenerator mockDailyExcelGen = mock(DailyStatementExcelGenerator.class);
        LeaveStatementExcelGenerator mockLeaveExcelGen = mock(LeaveStatementExcelGenerator.class);
        S3ReportStorageService mockS3v = mock(S3ReportStorageService.class);
        ReportHistoryRepository mockHistoryRepov = mock(ReportHistoryRepository.class);

        ReportController controller = new ReportController(List.of(mockGenerator), mockFileService, mockExcelGen, mockPlatoonExcelGen, mockMtExcelGen, mockDailyExcelGen, mockLeaveExcelGen, mockS3v, mockHistoryRepov);

        ResponseEntity<byte[]> response = controller.generatePlatoonChart(null, validSection, "en");

        assertEquals(200, response.getStatusCode().value(),
                "Valid section '" + validSection + "' should return 200");
    }

    @Property(tries = 100)
    void nullSection_returns200() {
        PdfReportGenerator mockGenerator = mock(PdfReportGenerator.class);
        when(mockGenerator.getReportType()).thenReturn("platoon_chart");
        when(mockGenerator.generate(any(ReportRequest.class))).thenReturn(new byte[]{1, 2, 3});

        ReportFileService mockFileService = mock(ReportFileService.class);
        SectionABExcelGenerator mockExcelGen = mock(SectionABExcelGenerator.class);
        PlatoonChartExcelGenerator mockPlatoonExcelGen = mock(PlatoonChartExcelGenerator.class);
        MtSectionExcelGenerator mockMtExcelGen = mock(MtSectionExcelGenerator.class);
        DailyStatementExcelGenerator mockDailyExcelGen = mock(DailyStatementExcelGenerator.class);
        LeaveStatementExcelGenerator mockLeaveExcelGen = mock(LeaveStatementExcelGenerator.class);
        S3ReportStorageService mockS3n = mock(S3ReportStorageService.class);
        ReportHistoryRepository mockHistoryRepon = mock(ReportHistoryRepository.class);

        ReportController controller = new ReportController(List.of(mockGenerator), mockFileService, mockExcelGen, mockPlatoonExcelGen, mockMtExcelGen, mockDailyExcelGen, mockLeaveExcelGen, mockS3n, mockHistoryRepon);

        ResponseEntity<byte[]> response = controller.generatePlatoonChart(null, null, "en");

        assertEquals(200, response.getStatusCode().value(),
                "Null section should return 200 (all sections)");
    }

    @Provide
    Arbitrary<String> invalidSections() {
        return Arbitraries.oneOf(
                // Single letters not in C-G
                Arbitraries.of("A", "B", "H", "Z", "a", "b", "h", "z"),
                // Numbers
                Arbitraries.of("1", "2", "0"),
                // Multi-character strings
                Arbitraries.of("CC", "CD", "CG", "AB"),
                // Empty string
                Arbitraries.of(""),
                // Special characters
                Arbitraries.of("!", "@", " ")
        );
    }

    @Provide
    Arbitrary<String> validSectionValues() {
        return Arbitraries.of("C", "D", "E", "F", "G", "c", "d", "e", "f", "g");
    }
}
