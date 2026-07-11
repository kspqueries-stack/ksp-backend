package com.policescheduler.controller;

import com.policescheduler.entity.ReportHistory;
import com.policescheduler.report.S3ReportStorageService;
import com.policescheduler.repository.ReportHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/reports/history")
public class ReportHistoryController {

    private final ReportHistoryRepository reportHistoryRepository;
    private final S3ReportStorageService s3ReportStorageService;

    public ReportHistoryController(ReportHistoryRepository reportHistoryRepository,
                                   S3ReportStorageService s3ReportStorageService) {
        this.reportHistoryRepository = reportHistoryRepository;
        this.s3ReportStorageService = s3ReportStorageService;
    }

    @GetMapping
    public ResponseEntity<Page<ReportHistory>> getReportHistory(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "generatedAt"));

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<ReportHistory> result;

        if (reportType != null && from != null && to != null) {
            result = reportHistoryRepository.findByReportTypeAndGeneratedAtBetween(reportType, from, to, pageable);
        } else if (reportType != null) {
            result = reportHistoryRepository.findByReportType(reportType, pageable);
        } else if (from != null && to != null) {
            result = reportHistoryRepository.findByGeneratedAtBetween(from, to, pageable);
        } else {
            result = reportHistoryRepository.findAll(pageable);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        return reportHistoryRepository.findById(id)
                .map(history -> {
                    byte[] data = s3ReportStorageService.download(history.getS3Key());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + history.getReportName() + "\"")
                            .contentType(MediaType.parseMediaType(
                                    history.getContentType() != null ? history.getContentType() : "application/octet-stream"))
                            .body(data);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
