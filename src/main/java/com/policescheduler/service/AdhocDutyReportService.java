package com.policescheduler.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.policescheduler.dto.AdhocAssigneeInfo;
import com.policescheduler.dto.AdhocDutyResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AdhocDutyReportService {

    private final AdhocDutyService adhocDutyService;

    private static final com.lowagie.text.Font TITLE_FONT = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD);
    private static final com.lowagie.text.Font SUBTITLE_FONT = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.BOLD);
    private static final com.lowagie.text.Font HEADER_FONT = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.BOLD, Color.BLACK);
    private static final com.lowagie.text.Font CELL_FONT = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL, Color.BLACK);
    private static final com.lowagie.text.Font FOOTER_FONT = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 7, com.lowagie.text.Font.ITALIC, Color.GRAY);

    public AdhocDutyReportService(AdhocDutyService adhocDutyService) {
        this.adhocDutyService = adhocDutyService;
    }

    public byte[] generatePdf(Long adhocDutyId) {
        AdhocDutyResponse duty = adhocDutyService.getAdhocDutyDetails(adhocDutyId);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Header
            Paragraph header = new Paragraph("Adhoc Duty Report", TITLE_FONT);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);
            document.add(new Paragraph("\n"));

            // Sub-header with duty details
            Paragraph subHeader = new Paragraph();
            subHeader.setFont(SUBTITLE_FONT);
            subHeader.add(new Chunk("Duty: " + duty.dutyName()));
            subHeader.add(new Chunk("    |    Date: " + duty.date()));
            subHeader.add(new Chunk("    |    Time: " + duty.startTime() + " - " + duty.endTime()));
            if (duty.location() != null && !duty.location().isBlank()) {
                subHeader.add(new Chunk("    |    Location: " + duty.location()));
            }
            document.add(subHeader);
            document.add(new Paragraph("\n"));

            // Table
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{5, 20, 12, 15, 15, 15, 12, 10});

            // Table headers
            String[] headers = {"#", "Name", "Badge ID", "Designation", "Section", "Original Duty", "Original Shift", "Status"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
                cell.setBackgroundColor(new Color(211, 211, 211));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(4);
                table.addCell(cell);
            }

            // Data rows
            List<AdhocAssigneeInfo> assignees = duty.assignees();
            if (assignees != null) {
                int idx = 1;
                for (AdhocAssigneeInfo a : assignees) {
                    addCell(table, String.valueOf(idx++));
                    addCell(table, a.personName() != null ? a.personName() : "");
                    addCell(table, a.badgeId() != null ? a.badgeId() : "");
                    addCell(table, a.designation() != null ? a.designation() : "");
                    addCell(table, a.section() != null ? a.section() : "");
                    addCell(table, a.originalDutyName() != null ? a.originalDutyName() : "");
                    addCell(table, a.originalShift() != null ? a.originalShift() : "");
                    addCell(table, a.status() != null ? a.status() : "");
                }
            }

            document.add(table);
            document.add(new Paragraph("\n"));

            // Footer
            String generated = "Generated on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
            int total = assignees != null ? assignees.size() : 0;
            Paragraph footer = new Paragraph(generated + "  |  Total: " + total + " personnel", FOOTER_FONT);
            footer.setAlignment(Element.ALIGN_RIGHT);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report for adhoc duty " + adhocDutyId, e);
        }
    }

    public byte[] generateExcel(Long adhocDutyId) {
        AdhocDutyResponse duty = adhocDutyService.getAdhocDutyDetails(adhocDutyId);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            String sheetName = duty.dutyName() != null ?
                duty.dutyName().substring(0, Math.min(31, duty.dutyName().length())) : "Adhoc Duty";
            Sheet sheet = workbook.createSheet(sheetName);

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Row 0: Duty info
            org.apache.poi.ss.usermodel.Row infoRow = sheet.createRow(0);
            infoRow.createCell(0).setCellValue("Duty: " + duty.dutyName());
            infoRow.createCell(2).setCellValue("Date: " + duty.date());
            infoRow.createCell(4).setCellValue("Time: " + duty.startTime() + " - " + duty.endTime());
            if (duty.location() != null) {
                infoRow.createCell(6).setCellValue("Location: " + duty.location());
            }

            // Row 2: Column headers
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(2);
            String[] headers = {"#", "Name", "Badge ID", "Designation", "Section", "Original Duty", "Original Shift", "Status"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            List<AdhocAssigneeInfo> assignees = duty.assignees();
            if (assignees != null) {
                int rowIdx = 3;
                int idx = 1;
                for (AdhocAssigneeInfo a : assignees) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(idx++);
                    row.createCell(1).setCellValue(a.personName() != null ? a.personName() : "");
                    row.createCell(2).setCellValue(a.badgeId() != null ? a.badgeId() : "");
                    row.createCell(3).setCellValue(a.designation() != null ? a.designation() : "");
                    row.createCell(4).setCellValue(a.section() != null ? a.section() : "");
                    row.createCell(5).setCellValue(a.originalDutyName() != null ? a.originalDutyName() : "");
                    row.createCell(6).setCellValue(a.originalShift() != null ? a.originalShift() : "");
                    row.createCell(7).setCellValue(a.status() != null ? a.status() : "");
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Excel report for adhoc duty " + adhocDutyId, e);
        }
    }

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, CELL_FONT));
        cell.setPadding(3);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
}
