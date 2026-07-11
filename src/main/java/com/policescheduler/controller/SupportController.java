package com.policescheduler.controller;

import com.policescheduler.dto.CreateSupportTicketRequest;
import com.policescheduler.dto.SupportTicketDto;
import com.policescheduler.entity.SupportTicket;
import com.policescheduler.service.SupportTicketService;
import com.policescheduler.service.TicketPdfService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support/tickets")
public class SupportController {

    private final SupportTicketService supportTicketService;
    private final TicketPdfService ticketPdfService;

    public SupportController(SupportTicketService supportTicketService, TicketPdfService ticketPdfService) {
        this.supportTicketService = supportTicketService;
        this.ticketPdfService = ticketPdfService;
    }

    @PostMapping
    public ResponseEntity<SupportTicketDto> submit(
            @Valid @RequestBody CreateSupportTicketRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        SupportTicketDto ticket = supportTicketService.submit(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @GetMapping
    public ResponseEntity<List<SupportTicketDto>> list(Authentication authentication) {
        String role = extractRole(authentication);

        if ("ADMIN".equals(role) || "SUPPORT_ADMIN".equals(role)) {
            return ResponseEntity.ok(supportTicketService.getAllTickets());
        }
        return ResponseEntity.ok(supportTicketService.getTicketsForUser(authentication.getName()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_ADMIN')")
    public ResponseEntity<List<SupportTicketDto>> getAllTickets() {
        return ResponseEntity.ok(supportTicketService.getAllTickets());
    }

    @PutMapping("/{id}/respond")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_ADMIN')")
    public ResponseEntity<SupportTicketDto> respond(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String response = body.getOrDefault("response", "");
        return ResponseEntity.ok(supportTicketService.respondToTicket(id, response));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_ADMIN')")
    public ResponseEntity<SupportTicketDto> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "OPEN");
        return ResponseEntity.ok(supportTicketService.updateStatus(id, status));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_ADMIN')")
    public ResponseEntity<SupportTicketDto> patchStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "OPEN");
        return ResponseEntity.ok(supportTicketService.updateStatus(id, status));
    }

    @PostMapping("/{id}/comment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_ADMIN')")
    public ResponseEntity<SupportTicketDto> addComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String comment = body.getOrDefault("comment", "");
        return ResponseEntity.ok(supportTicketService.addComment(id, comment));
    }

    @PatchMapping("/{id}/reopen")
    public ResponseEntity<SupportTicketDto> reopenTicket(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.reopenTicket(id));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_ADMIN')")
    public ResponseEntity<byte[]> downloadTicketPdf(@PathVariable Long id) {
        SupportTicket ticket = supportTicketService.getTicketEntity(id);
        byte[] pdf = ticketPdfService.generateSingleTicketPdf(ticket);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "ticket-" + id + ".pdf");

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_ADMIN')")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<SupportTicketDto> tickets = supportTicketService.getTicketsForReport(status, priority, from, to);

        // Convert DTOs back to entities for PDF generation (simplified approach)
        List<SupportTicket> ticketEntities = tickets.stream().map(dto -> {
            SupportTicket t = new SupportTicket();
            t.setId(dto.getId());
            t.setSubject(dto.getSubject());
            t.setMessage(dto.getMessage());
            t.setCategory(dto.getCategory());
            t.setPriority(dto.getPriority());
            t.setStatus(dto.getStatus());
            t.setSubmittedBy(dto.getSubmittedBy());
            t.setAdminResponse(dto.getAdminResponse());
            t.setCreatedAt(dto.getCreatedAt());
            t.setUpdatedAt(dto.getUpdatedAt());
            return t;
        }).toList();

        String title = "Support Ticket Report";
        byte[] pdf = ticketPdfService.generateTicketReportPdf(ticketEntities, title);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "ticket-report.pdf");

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("GENERAL_USER");
    }
}
