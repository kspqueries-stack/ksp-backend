package com.policescheduler.controller;

import com.policescheduler.entity.ReportHistory;
import com.policescheduler.report.PdfReportGenerator;
import com.policescheduler.report.ReportFileService;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.report.ReportRequest;
import com.policescheduler.report.S3ReportStorageService;
import com.policescheduler.report.generator.DailyStatementExcelGenerator;
import com.policescheduler.report.generator.LeaveStatementExcelGenerator;
import com.policescheduler.report.generator.MtSectionExcelGenerator;
import com.policescheduler.report.generator.PlatoonChartExcelGenerator;
import com.policescheduler.report.generator.SectionABExcelGenerator;
import com.policescheduler.repository.ReportHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final Map<String, PdfReportGenerator> generatorsByType;
    private final ReportFileService reportFileService;
    private final SectionABExcelGenerator sectionABExcelGenerator;
    private final PlatoonChartExcelGenerator platoonChartExcelGenerator;
    private final MtSectionExcelGenerator mtSectionExcelGenerator;
    private final DailyStatementExcelGenerator dailyStatementExcelGenerator;
    private final LeaveStatementExcelGenerator leaveStatementExcelGenerator;
    private final S3ReportStorageService s3ReportStorageService;
    private final ReportHistoryRepository reportHistoryRepository;

    public ReportController(List<PdfReportGenerator> generators, ReportFileService reportFileService,
                            SectionABExcelGenerator sectionABExcelGenerator,
                            PlatoonChartExcelGenerator platoonChartExcelGenerator,
                            MtSectionExcelGenerator mtSectionExcelGenerator,
                            DailyStatementExcelGenerator dailyStatementExcelGenerator,
                            LeaveStatementExcelGenerator leaveStatementExcelGenerator,
                            S3ReportStorageService s3ReportStorageService,
                            ReportHistoryRepository reportHistoryRepository) {
        this.reportFileService = reportFileService;
        this.sectionABExcelGenerator = sectionABExcelGenerator;
        this.platoonChartExcelGenerator = platoonChartExcelGenerator;
        this.mtSectionExcelGenerator = mtSectionExcelGenerator;
        this.dailyStatementExcelGenerator = dailyStatementExcelGenerator;
        this.leaveStatementExcelGenerator = leaveStatementExcelGenerator;
        this.s3ReportStorageService = s3ReportStorageService;
        this.reportHistoryRepository = reportHistoryRepository;
        this.generatorsByType = new HashMap<>();
        for (PdfReportGenerator generator : generators) {
            this.generatorsByType.put(generator.getReportType(), generator);
        }
    }

    private static final Set<String> VALID_PLATOON_SECTIONS = Set.of("C", "D", "E", "F", "G");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @GetMapping("/platoon-chart")
    public ResponseEntity<byte[]> generatePlatoonChart(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String section,
            @RequestParam(required = false, defaultValue = "en") String locale) {
        // Validate section parameter if provided
        if (section != null) {
            String upper = section.toUpperCase();
            if (!VALID_PLATOON_SECTIONS.contains(upper)) {
                return ResponseEntity.badRequest().build();
            }
            section = upper;
        }
        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(date != null ? date : LocalDate.now())
                .section(section)
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("platoon_chart").generate(request);
        String filename = "Section_C_" + LocalDate.now().format(DATE_FMT) + ".pdf";
        storeReportAsync("platoon_chart", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    @GetMapping("/form-168")
    public ResponseEntity<byte[]> generateForm168(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false, defaultValue = "en") String locale) {
        LocalDate reportDate = date != null ? date : LocalDate.now();
        ReportRequest request = ReportRequest.builder()
                .reportType("form_168")
                .date(reportDate)
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("form_168").generate(request);
        String filename = "STATEMENT " + reportDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".pdf";
        storeReportAsync("form_168", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    @GetMapping("/section-a")
    public ResponseEntity<byte[]> generateSectionA(
            @RequestParam(required = false, defaultValue = "en") String locale) {
        ReportRequest request = ReportRequest.builder()
                .reportType("section_ab")
                .section("A")
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("section_ab").generate(request);
        String filename = "Section_A_" + LocalDate.now().format(DATE_FMT) + ".pdf";
        storeReportAsync("section_a", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    @GetMapping("/section-b")
    public ResponseEntity<byte[]> generateSectionB(
            @RequestParam(required = false, defaultValue = "en") String locale) {
        ReportRequest request = ReportRequest.builder()
                .reportType("section_ab")
                .section("B")
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("section_ab").generate(request);
        String filename = "Section_B_" + LocalDate.now().format(DATE_FMT) + ".pdf";
        storeReportAsync("section_b", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    @GetMapping("/section-ab")
    public ResponseEntity<byte[]> generateSectionAB(
            @RequestParam(required = false, defaultValue = "en") String locale) {
        ReportRequest request = ReportRequest.builder()
                .reportType("section_ab")
                .combined(true)
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("section_ab").generate(request);
        String filename = "Section_AB_" + LocalDate.now().format(DATE_FMT) + ".pdf";
        storeReportAsync("section_ab", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    @GetMapping("/mt-section")
    public ResponseEntity<byte[]> generateMtSection(
            @RequestParam(required = false, defaultValue = "en") String locale) {
        ReportRequest request = ReportRequest.builder()
                .reportType("mt_section")
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("mt_section").generate(request);
        String filename = "MT_Section_" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".pdf";
        storeReportAsync("mt_section", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    @GetMapping("/personnel")
    public ResponseEntity<byte[]> generatePersonnelReport(
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String dutyType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false, defaultValue = "en") String locale) {
        ReportRequest request = ReportRequest.builder()
                .reportType("personnel_list")
                .section(section)
                .designation(designation)
                .dutyType(dutyType)
                .isActive(isActive)
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("personnel_list").generate(request);
        String filename = "Personnel_List_" + LocalDate.now().format(DATE_FMT) + ".pdf";
        storeReportAsync("personnel_list", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    @GetMapping("/leave-statement")
    public ResponseEntity<byte[]> generateLeaveStatement(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String leaveType,
            @RequestParam(required = false, defaultValue = "en") String locale) {
        ReportRequest request = ReportRequest.builder()
                .reportType("leave_statement")
                .leaveStatus(status)
                .leaveType(leaveType)
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("leave_statement").generate(request);
        String filename = "Leave_Statement_" + LocalDate.now().format(DATE_FMT) + ".pdf";
        storeReportAsync("leave_statement", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    // Backward-compatible alias: frontend calls /leave-requests
    @GetMapping("/leave-requests")
    public ResponseEntity<byte[]> generateLeaveRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String leaveType,
            @RequestParam(required = false, defaultValue = "en") String locale) {
        return generateLeaveStatement(status, leaveType, locale);
    }

    // Schedule report: generates the Section A&B combined report as the "schedule" view
    @GetMapping("/schedules")
    public ResponseEntity<byte[]> generateScheduleReport(
            @RequestParam(required = false, defaultValue = "en") String locale) {
        ReportRequest request = ReportRequest.builder()
                .reportType("section_ab")
                .combined(true)
                .locale(locale)
                .build();
        byte[] pdf = generatorsByType.get("section_ab").generate(request);
        String filename = "Schedule_Report_" + LocalDate.now().format(DATE_FMT) + ".pdf";
        storeReportAsync("schedule", filename, pdf, "application/pdf");
        return pdfResponse(pdf, filename);
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String fileId) {
        byte[] pdf = reportFileService.retrieve(fileId);
        if (pdf == null) {
            return ResponseEntity.notFound().build();
        }
        return pdfResponse(pdf, fileId + ".pdf");
    }

    @GetMapping("/section-a/excel")
    public ResponseEntity<byte[]> generateSectionAExcel() {
        byte[] excel = sectionABExcelGenerator.generate("A");
        String filename = "Section_A_" + LocalDate.now().format(DATE_FMT) + ".xlsx";
        storeReportAsync("section_a", filename, excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return excelResponse(excel, filename);
    }

    @GetMapping("/section-b/excel")
    public ResponseEntity<byte[]> generateSectionBExcel() {
        byte[] excel = sectionABExcelGenerator.generate("B");
        String filename = "Section_B_" + LocalDate.now().format(DATE_FMT) + ".xlsx";
        storeReportAsync("section_b", filename, excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return excelResponse(excel, filename);
    }

    @GetMapping("/section-ab/excel")
    public ResponseEntity<byte[]> generateSectionABExcel() {
        byte[] excel = sectionABExcelGenerator.generateCombined();
        String filename = "Section_AB_" + LocalDate.now().format(DATE_FMT) + ".xlsx";
        storeReportAsync("section_ab", filename, excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return excelResponse(excel, filename);
    }

    @GetMapping("/platoon-chart/excel")
    public ResponseEntity<byte[]> generatePlatoonChartExcel() {
        byte[] excel = platoonChartExcelGenerator.generate();
        String filename = "Section_C_" + LocalDate.now().format(DATE_FMT) + ".xlsx";
        storeReportAsync("platoon_chart", filename, excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return excelResponse(excel, filename);
    }

    @GetMapping("/mt-section/excel")
    public ResponseEntity<byte[]> generateMtSectionExcel() {
        byte[] excel = mtSectionExcelGenerator.generate();
        String filename = "MT_Section_" + LocalDate.now().format(DATE_FMT) + ".xlsx";
        storeReportAsync("mt_section", filename, excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return excelResponse(excel, filename);
    }

    @GetMapping("/form-168/excel")
    public ResponseEntity<byte[]> generateDailyStatementExcel(
            @RequestParam(required = false) LocalDate date) {
        LocalDate reportDate = date != null ? date : LocalDate.now();
        byte[] excel = dailyStatementExcelGenerator.generate(reportDate);
        String filename = "STATEMENT " + reportDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".xlsx";
        storeReportAsync("form_168", filename, excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return excelResponse(excel, filename);
    }

    @GetMapping("/leave-statement/excel")
    public ResponseEntity<byte[]> generateLeaveStatementExcel(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String leaveType) {
        byte[] excel = leaveStatementExcelGenerator.generate(status, leaveType);
        String filename = "Leave_Statement_" + LocalDate.now().format(DATE_FMT) + ".xlsx";
        storeReportAsync("leave_statement", filename, excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return excelResponse(excel, filename);
    }

    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<Map<String, String>> handleReportError(ReportGenerationException ex) {
        return ResponseEntity.status(500)
                .body(Map.of("error", ex.getMessage()));
    }

    private ResponseEntity<byte[]> excelResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private String getDateStr() {
        return LocalDate.now().format(DATE_FMT);
    }

    private void storeReportAsync(String reportType, String filename, byte[] data, String contentType) {
        CompletableFuture.runAsync(() -> {
            try {
                String s3Key = s3ReportStorageService.upload(reportType, filename, data, contentType);
                ReportHistory history = new ReportHistory();
                history.setReportName(filename);
                history.setReportType(reportType);
                history.setS3Key(s3Key);
                history.setFileSize((long) data.length);
                history.setContentType(contentType);
                reportHistoryRepository.save(history);
            } catch (Exception e) {
                log.warn("Failed to store report in S3: {}", e.getMessage());
            }
        });
    }
}
