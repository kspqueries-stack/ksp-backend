package com.policescheduler.service;

import com.policescheduler.dto.CreateSupportTicketRequest;
import com.policescheduler.dto.SupportTicketDto;
import com.policescheduler.entity.SupportTicket;
import com.policescheduler.repository.SupportTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupportTicketService {

    private final SupportTicketRepository repository;
    private final EmailNotificationService emailNotificationService;

    public SupportTicketService(SupportTicketRepository repository, EmailNotificationService emailNotificationService) {
        this.repository = repository;
        this.emailNotificationService = emailNotificationService;
    }

    @Transactional
    public SupportTicketDto submit(CreateSupportTicketRequest request, String username) {
        SupportTicket ticket = new SupportTicket();
        ticket.setSubject(request.getSubject());
        ticket.setMessage(request.getMessage());
        ticket.setCategory(request.getCategory());
        ticket.setPriority(request.getPriority() != null ? request.getPriority() : "MEDIUM");
        ticket.setStatus("OPEN");
        ticket.setSubmittedBy(username);

        SupportTicket saved = repository.save(ticket);

        // Send email notification asynchronously
        emailNotificationService.sendTicketNotification(
                saved.getId(),
                saved.getSubject(),
                saved.getMessage(),
                saved.getCategory(),
                saved.getPriority(),
                username
        );

        return toDto(saved);
    }

    public List<SupportTicketDto> getTicketsForUser(String username) {
        return repository.findBySubmittedByOrderByCreatedAtDesc(username)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<SupportTicketDto> getAllTickets() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupportTicketDto respondToTicket(Long id, String response) {
        SupportTicket ticket = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));
        ticket.setAdminResponse(response);
        ticket.setStatus("IN_PROGRESS");
        return toDto(repository.save(ticket));
    }

    @Transactional
    public SupportTicketDto updateStatus(Long id, String status) {
        SupportTicket ticket = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));
        ticket.setStatus(status);
        return toDto(repository.save(ticket));
    }

    @Transactional
    public SupportTicketDto addComment(Long id, String comment) {
        SupportTicket ticket = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));
        ticket.setAdminResponse(comment);
        return toDto(repository.save(ticket));
    }

    @Transactional
    public SupportTicketDto reopenTicket(Long id) {
        SupportTicket ticket = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));
        ticket.setStatus("OPEN");
        return toDto(repository.save(ticket));
    }

    public List<SupportTicketDto> getTicketsForReport(String status, String priority, LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(23, 59, 59) : null;
        return repository.findTicketsFiltered(status, priority, fromDateTime, toDateTime)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public SupportTicket getTicketEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));
    }

    private SupportTicketDto toDto(SupportTicket ticket) {
        return SupportTicketDto.builder()
                .id(ticket.getId())
                .subject(ticket.getSubject())
                .message(ticket.getMessage())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .submittedBy(ticket.getSubmittedBy())
                .adminResponse(ticket.getAdminResponse())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}
