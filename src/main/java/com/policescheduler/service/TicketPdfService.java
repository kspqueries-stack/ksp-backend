package com.policescheduler.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.policescheduler.entity.SupportTicket;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TicketPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(49, 46, 129));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);

    public byte[] generateSingleTicketPdf(SupportTicket ticket) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            // Title
            Paragraph title = new Paragraph("Support Ticket #" + ticket.getId(), TITLE_FONT);
            title.setSpacingAfter(20);
            document.add(title);

            // Ticket details table
            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(100);
            detailsTable.setWidths(new float[]{1, 3});
            detailsTable.setSpacingAfter(15);

            addDetailRow(detailsTable, "Subject:", ticket.getSubject());
            addDetailRow(detailsTable, "Status:", ticket.getStatus());
            addDetailRow(detailsTable, "Priority:", ticket.getPriority());
            addDetailRow(detailsTable, "Category:", ticket.getCategory());
            addDetailRow(detailsTable, "Submitted By:", ticket.getSubmittedBy());
            addDetailRow(detailsTable, "Created:", ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DATE_FMT) : "N/A");
            addDetailRow(detailsTable, "Updated:", ticket.getUpdatedAt() != null ? ticket.getUpdatedAt().format(DATE_FMT) : "N/A");

            document.add(detailsTable);

            // Message
            Paragraph msgLabel = new Paragraph("Message:", LABEL_FONT);
            msgLabel.setSpacingAfter(5);
            document.add(msgLabel);

            Paragraph msgBody = new Paragraph(ticket.getMessage(), VALUE_FONT);
            msgBody.setSpacingAfter(15);
            document.add(msgBody);

            // Admin Response
            if (ticket.getAdminResponse() != null && !ticket.getAdminResponse().isEmpty()) {
                Paragraph respLabel = new Paragraph("Admin Response:", LABEL_FONT);
                respLabel.setSpacingAfter(5);
                document.add(respLabel);

                Paragraph respBody = new Paragraph(ticket.getAdminResponse(), VALUE_FONT);
                respBody.setSpacingAfter(15);
                document.add(respBody);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ticket PDF", e);
        }
    }

    public byte[] generateTicketReportPdf(List<SupportTicket> tickets, String title) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
            PdfWriter.getInstance(document, out);
            document.open();

            // Title
            Paragraph titlePara = new Paragraph(title, TITLE_FONT);
            titlePara.setSpacingAfter(10);
            document.add(titlePara);

            // Summary
            Paragraph summary = new Paragraph("Total Tickets: " + tickets.size(), VALUE_FONT);
            summary.setSpacingAfter(15);
            document.add(summary);

            if (tickets.isEmpty()) {
                document.add(new Paragraph("No tickets found matching the criteria.", VALUE_FONT));
            } else {
                // Table
                PdfPTable table = new PdfPTable(7);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{0.5f, 2.5f, 1f, 1f, 1.2f, 1.2f, 1.5f});

                // Header row
                String[] headers = {"ID", "Subject", "Priority", "Status", "Category", "Submitted By", "Date"};
                for (String header : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
                    cell.setBackgroundColor(new Color(49, 46, 129));
                    cell.setPadding(6);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }

                // Data rows
                for (SupportTicket ticket : tickets) {
                    addCell(table, String.valueOf(ticket.getId()));
                    addCell(table, truncate(ticket.getSubject(), 40));
                    addCell(table, ticket.getPriority());
                    addCell(table, ticket.getStatus());
                    addCell(table, ticket.getCategory());
                    addCell(table, ticket.getSubmittedBy());
                    addCell(table, ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DATE_FMT) : "N/A");
                }

                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ticket report PDF", e);
        }
    }

    private void addDetailRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(0);
        labelCell.setPadding(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "N/A", VALUE_FONT));
        valueCell.setBorder(0);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", BODY_FONT));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
