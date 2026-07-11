package com.policescheduler.report.generator;

import com.policescheduler.entity.LeaveRequest;
import com.policescheduler.entity.Personnel;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.repository.LeaveRequestRepository;
import com.policescheduler.repository.PersonnelRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaveStatementExcelGenerator {

    private static final Logger log = LoggerFactory.getLogger(LeaveStatementExcelGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final LeaveRequestRepository leaveRequestRepository;
    private final PersonnelRepository personnelRepository;

    public LeaveStatementExcelGenerator(LeaveRequestRepository leaveRequestRepository,
                                         PersonnelRepository personnelRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.personnelRepository = personnelRepository;
    }

    public byte[] generate(String status, String leaveType) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Leave Statement");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);

            int rowIndex = 0;

            // Title
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("LEAVE STATEMENT");
            titleCell.setCellStyle(headerStyle);

            rowIndex++; // blank

            // Header row
            Row headerRow = sheet.createRow(rowIndex++);
            String[] headers = {"SL.NO", "BADGE ID", "NAME", "DESIGNATION", "LEAVE TYPE", "START DATE", "END DATE", "DAYS", "STATUS", "REASON"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Get leave requests
            List<LeaveRequest> leaves = leaveRequestRepository.findAll().stream()
                    .filter(lr -> status == null || status.isEmpty() || status.equalsIgnoreCase(lr.getStatus()))
                    .filter(lr -> leaveType == null || leaveType.isEmpty() || leaveType.equalsIgnoreCase(lr.getLeaveType()))
                    .sorted(Comparator.comparing(LeaveRequest::getStartDate).reversed())
                    .collect(Collectors.toList());

            Map<Long, Personnel> personnelMap = personnelRepository.findAllById(
                    leaves.stream().map(LeaveRequest::getPersonnelId)
                            .distinct().collect(Collectors.toList())
            ).stream().collect(Collectors.toMap(Personnel::getId, p -> p));

            // Group by leave type
            Map<String, List<LeaveRequest>> byType = leaves.stream()
                    .collect(Collectors.groupingBy(LeaveRequest::getLeaveType, LinkedHashMap::new, Collectors.toList()));

            int slNo = 1;
            for (Map.Entry<String, List<LeaveRequest>> entry : byType.entrySet()) {
                // Section header
                Row sectionRow = sheet.createRow(rowIndex++);
                Cell secCell = sectionRow.createCell(0);
                secCell.setCellValue(entry.getKey());
                secCell.setCellStyle(sectionStyle);

                for (LeaveRequest lr : entry.getValue()) {
                    Personnel p = personnelMap.get(lr.getPersonnelId());
                    if (p == null) continue;

                    Row row = sheet.createRow(rowIndex++);
                    long days = ChronoUnit.DAYS.between(lr.getStartDate(), lr.getEndDate()) + 1;

                    row.createCell(0).setCellValue(slNo++);
                    row.createCell(1).setCellValue(p.getBadgeId() != null ? p.getBadgeId() : "");
                    row.createCell(2).setCellValue(p.getPersonName() != null ? p.getPersonName() : "");
                    row.createCell(3).setCellValue(p.getDesignation() != null ? p.getDesignation() : "");
                    row.createCell(4).setCellValue(lr.getLeaveType());
                    row.createCell(5).setCellValue(lr.getStartDate().format(DATE_FORMAT));
                    row.createCell(6).setCellValue(lr.getEndDate().format(DATE_FORMAT));
                    row.createCell(7).setCellValue(days);
                    row.createCell(8).setCellValue(lr.getStatus());
                    row.createCell(9).setCellValue(lr.getReason() != null ? lr.getReason() : "");

                    for (int i = 0; i < 10; i++) {
                        row.getCell(i).setCellStyle(dataStyle);
                    }
                }
            }

            // Auto-size
            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Leave Statement Excel report", e);
            throw new ReportGenerationException("leave_statement_excel",
                    "Failed to generate Leave Statement Excel report", e);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createSectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 8);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
