package com.policescheduler.controller;

import com.policescheduler.dto.AdhocAssigneeInfo;
import com.policescheduler.dto.AdhocDutyResponse;
import com.policescheduler.dto.CreateAdhocDutyRequest;
import com.policescheduler.service.AdhocDutyReportService;
import com.policescheduler.service.AdhocDutyService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/adhoc-duties")
public class AdhocDutyController {

    private final AdhocDutyService adhocDutyService;
    private final AdhocDutyReportService adhocDutyReportService;

    public AdhocDutyController(AdhocDutyService adhocDutyService, AdhocDutyReportService adhocDutyReportService) {
        this.adhocDutyService = adhocDutyService;
        this.adhocDutyReportService = adhocDutyReportService;
    }

    @PostMapping
    public ResponseEntity<AdhocDutyResponse> create(@Valid @RequestBody CreateAdhocDutyRequest request) {
        AdhocDutyResponse response = adhocDutyService.createAdhocDuty(request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AdhocDutyResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status) {
        List<AdhocDutyResponse> response = adhocDutyService.listAdhocDuties(date, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search-personnel")
    public ResponseEntity<List<AdhocAssigneeInfo>> searchPersonnel(
            @RequestParam String query,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime) {
        List<AdhocAssigneeInfo> response = adhocDutyService.searchAvailablePersonnel(query, date, startTime, endTime);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdhocDutyResponse> getDetails(@PathVariable Long id) {
        AdhocDutyResponse response = adhocDutyService.getAdhocDutyDetails(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AdhocDutyResponse> cancel(@PathVariable Long id) {
        AdhocDutyResponse response = adhocDutyService.cancelAdhocDuty(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/preview")
    public ResponseEntity<List<AdhocAssigneeInfo>> preview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam int count,
            @RequestParam(required = false, defaultValue = "ALL_PLATOONS") String pickFrom,
            @RequestParam(required = false) List<Long> platoonIds) {
        List<AdhocAssigneeInfo> response = adhocDutyService.previewAvailablePersonnel(
            date, startTime, endTime, count, pickFrom, platoonIds);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/report/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = adhocDutyReportService.generatePdf(id);
        return ResponseEntity.ok()
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=adhoc-duty-" + id + ".pdf")
            .body(pdf);
    }

    @GetMapping("/{id}/report/excel")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable Long id) {
        byte[] excel = adhocDutyReportService.generateExcel(id);
        return ResponseEntity.ok()
            .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header("Content-Disposition", "attachment; filename=adhoc-duty-" + id + ".xlsx")
            .body(excel);
    }
}
