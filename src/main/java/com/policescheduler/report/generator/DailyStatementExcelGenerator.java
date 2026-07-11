package com.policescheduler.report.generator;

import com.policescheduler.entity.DutyAssignment;
import com.policescheduler.entity.DutyType;
import com.policescheduler.entity.LeaveRequest;
import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.SectionStrength;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.repository.DutyAssignmentRepository;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.LeaveRequestRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.SectionStrengthRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DailyStatementExcelGenerator {

    private static final Logger log = LoggerFactory.getLogger(DailyStatementExcelGenerator.class);

    private static final List<String> DESIGNATION_COLUMNS =
            List.of("DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC");

    private static final List<String> STRENGTH_ROWS = List.of(
            "C.A.R SANCTIONED STRENGTH",
            "PRESENT STRENGTH TOTAL",
            "ACTUAL VACANCY",
            "PRESENT STRENGTH PMT",
            "PRESENT STRENGTH COMPANY",
            "RECRUIT APC'S"
    );

    private final PersonnelRepository personnelRepository;
    private final DutyAssignmentRepository dutyAssignmentRepository;
    private final DutyTypeRepository dutyTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final SectionStrengthRepository sectionStrengthRepository;

    public DailyStatementExcelGenerator(PersonnelRepository personnelRepository,
                                         DutyAssignmentRepository dutyAssignmentRepository,
                                         DutyTypeRepository dutyTypeRepository,
                                         LeaveRequestRepository leaveRequestRepository,
                                         SectionStrengthRepository sectionStrengthRepository) {
        this.personnelRepository = personnelRepository;
        this.dutyAssignmentRepository = dutyAssignmentRepository;
        this.dutyTypeRepository = dutyTypeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.sectionStrengthRepository = sectionStrengthRepository;
    }

    public byte[] generate(LocalDate reportDate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Daily Statement");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle sectionHeaderStyle = createSectionHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle numStyle = createNumberStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowIndex = 0;

            // Title
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("FORM NO:168");
            titleCell.setCellStyle(headerStyle);

            Cell dateCell = titleRow.createCell(7);
            dateCell.setCellValue("DATE :" + reportDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            dateCell.setCellStyle(headerStyle);

            rowIndex++; // blank row

            // Get all active personnel
            List<Personnel> allPersonnel = personnelRepository.findAll().stream()
                    .filter(p -> p.getIsActive() != null && p.getIsActive())
                    .collect(Collectors.toList());

            Map<String, Long> personnelByDesignation = allPersonnel.stream()
                    .filter(p -> p.getDesignation() != null)
                    .collect(Collectors.groupingBy(Personnel::getDesignation, Collectors.counting()));

            // Get section strength data
            List<SectionStrength> strengthData = sectionStrengthRepository.findAll().stream()
                    .filter(s -> !s.getEffectiveDate().isAfter(reportDate))
                    .collect(Collectors.toList());

            Map<String, Integer> sanctionedByDesignation = new HashMap<>();
            Map<String, Integer> presentByDesignation = new HashMap<>();
            for (SectionStrength ss : strengthData) {
                sanctionedByDesignation.merge(ss.getDesignation(), ss.getSanctionedCount(), Integer::sum);
                presentByDesignation.merge(ss.getDesignation(), ss.getPresentCount(), Integer::sum);
            }

            // === STRENGTH TABLE ===
            Row strengthHeaderRow = sheet.createRow(rowIndex++);
            Cell headsCell = strengthHeaderRow.createCell(0);
            headsCell.setCellValue("HEADS");
            headsCell.setCellStyle(headerStyle);
            for (int i = 0; i < DESIGNATION_COLUMNS.size(); i++) {
                Cell cell = strengthHeaderRow.createCell(i + 1);
                cell.setCellValue(DESIGNATION_COLUMNS.get(i));
                cell.setCellStyle(headerStyle);
            }
            Cell totalHeaderCell = strengthHeaderRow.createCell(DESIGNATION_COLUMNS.size() + 1);
            totalHeaderCell.setCellValue("TOTAL");
            totalHeaderCell.setCellStyle(headerStyle);

            // Strength data rows
            for (String rowLabel : STRENGTH_ROWS) {
                Row row = sheet.createRow(rowIndex++);
                Cell labelCell = row.createCell(0);
                labelCell.setCellValue(rowLabel);
                labelCell.setCellStyle(dataStyle);
                int rowTotal = 0;
                for (int i = 0; i < DESIGNATION_COLUMNS.size(); i++) {
                    String col = DESIGNATION_COLUMNS.get(i);
                    int value = getStrengthValue(rowLabel, col, sanctionedByDesignation,
                            presentByDesignation, personnelByDesignation);
                    rowTotal += value;
                    Cell cell = row.createCell(i + 1);
                    cell.setCellValue(value);
                    cell.setCellStyle(numStyle);
                }
                Cell totCell = row.createCell(DESIGNATION_COLUMNS.size() + 1);
                totCell.setCellValue(rowTotal);
                totCell.setCellStyle(numStyle);
            }

            rowIndex++; // blank row

            // === CAR DUTY ABSTRACT ===
            Row carDutyHeader = sheet.createRow(rowIndex++);
            Cell carDutyCell = carDutyHeader.createCell(0);
            carDutyCell.setCellValue("CAR DUTY ABSTRACT");
            carDutyCell.setCellStyle(sectionHeaderStyle);

            // Duty abstract header
            Row dutyHeaderRow = sheet.createRow(rowIndex++);
            Cell dutyHeadsCell = dutyHeaderRow.createCell(0);
            dutyHeadsCell.setCellValue("HEADS");
            dutyHeadsCell.setCellStyle(headerStyle);
            String[] dutyColumns = {"RPI", "RSI", "ARSI", "AHC", "APC", "SWP", "TOTAL"};
            for (int i = 0; i < dutyColumns.length; i++) {
                Cell cell = dutyHeaderRow.createCell(i + 1);
                cell.setCellValue(dutyColumns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Get duty assignments
            List<DutyAssignment> currentAssignments = dutyAssignmentRepository.findByIsCurrentTrue();
            List<DutyType> allDutyTypes = dutyTypeRepository.findAll();
            Map<Long, DutyType> dutyTypeMap = allDutyTypes.stream()
                    .collect(Collectors.toMap(DutyType::getId, d -> d));
            Map<Long, Personnel> personnelMap = personnelRepository.findAllById(
                    currentAssignments.stream().map(DutyAssignment::getPersonnelId)
                            .distinct().collect(Collectors.toList())
            ).stream().collect(Collectors.toMap(Personnel::getId, p -> p));

            // Group by parent duty type (hierarchical)
            Map<String, List<DutyAssignment>> assignmentsByCategory = new LinkedHashMap<>();
            for (DutyAssignment assignment : currentAssignments) {
                DutyType dt = dutyTypeMap.get(assignment.getDutyTypeId());
                String category;
                if (dt != null) {
                    if (dt.getParent() != null) {
                        category = dt.getParent().getName();
                    } else {
                        category = dt.getName();
                    }
                } else {
                    category = "Unknown";
                }
                assignmentsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(assignment);
            }

            // Write duty rows
            for (Map.Entry<String, List<DutyAssignment>> entry : assignmentsByCategory.entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                Cell catCell = row.createCell(0);
                catCell.setCellValue(entry.getKey());
                catCell.setCellStyle(dataStyle);

                Map<String, Long> countsByDesignation = entry.getValue().stream()
                        .map(a -> personnelMap.get(a.getPersonnelId()))
                        .filter(Objects::nonNull)
                        .filter(p -> p.getDesignation() != null)
                        .collect(Collectors.groupingBy(Personnel::getDesignation, Collectors.counting()));

                int rowTotal = 0;
                String[] desigCols = {"RPI", "RSI", "ARSI", "AHC", "APC"};
                for (int i = 0; i < desigCols.length; i++) {
                    long count = countsByDesignation.getOrDefault(desigCols[i], 0L);
                    rowTotal += (int) count;
                    Cell cell = row.createCell(i + 1);
                    cell.setCellValue(count);
                    cell.setCellStyle(numStyle);
                }
                // SWP column (always 0)
                Cell swpCell = row.createCell(desigCols.length + 1);
                swpCell.setCellValue(0);
                swpCell.setCellStyle(numStyle);
                // Total
                Cell totCell = row.createCell(desigCols.length + 2);
                totCell.setCellValue(rowTotal);
                totCell.setCellStyle(numStyle);
            }

            rowIndex++; // blank row

            // === LEAVE SECTION ===
            Row leaveHeader = sheet.createRow(rowIndex++);
            Cell leaveHeaderCell = leaveHeader.createCell(0);
            leaveHeaderCell.setCellValue("LEAVE, W/OFF, PERMISSION, ABSENT, SICK, SUSPENSION");
            leaveHeaderCell.setCellStyle(sectionHeaderStyle);

            List<LeaveRequest> activeLeaves = leaveRequestRepository.findAll().stream()
                    .filter(lr -> "APPROVED".equals(lr.getStatus()))
                    .filter(lr -> !lr.getStartDate().isAfter(reportDate) && !lr.getEndDate().isBefore(reportDate))
                    .collect(Collectors.toList());

            Map<Long, Personnel> leavePersonnelMap = personnelRepository.findAllById(
                    activeLeaves.stream().map(LeaveRequest::getPersonnelId)
                            .distinct().collect(Collectors.toList())
            ).stream().collect(Collectors.toMap(Personnel::getId, p -> p));

            // Group by leave type
            Map<String, List<LeaveRequest>> leavesByType = activeLeaves.stream()
                    .collect(Collectors.groupingBy(LeaveRequest::getLeaveType));

            // Leave type header
            Row leaveTypeHeaderRow = sheet.createRow(rowIndex++);
            Cell leaveTypeHeadCell = leaveTypeHeaderRow.createCell(0);
            leaveTypeHeadCell.setCellValue("TYPE");
            leaveTypeHeadCell.setCellStyle(headerStyle);
            for (int i = 0; i < DESIGNATION_COLUMNS.size(); i++) {
                Cell cell = leaveTypeHeaderRow.createCell(i + 1);
                cell.setCellValue(DESIGNATION_COLUMNS.get(i));
                cell.setCellStyle(headerStyle);
            }
            Cell leaveTotalHeader = leaveTypeHeaderRow.createCell(DESIGNATION_COLUMNS.size() + 1);
            leaveTotalHeader.setCellValue("TOTAL");
            leaveTotalHeader.setCellStyle(headerStyle);

            for (Map.Entry<String, List<LeaveRequest>> entry : leavesByType.entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                Cell typeCell = row.createCell(0);
                typeCell.setCellValue(entry.getKey());
                typeCell.setCellStyle(dataStyle);

                Map<String, Long> countsByDesignation = entry.getValue().stream()
                        .map(lr -> leavePersonnelMap.get(lr.getPersonnelId()))
                        .filter(Objects::nonNull)
                        .filter(p -> p.getDesignation() != null)
                        .collect(Collectors.groupingBy(Personnel::getDesignation, Collectors.counting()));

                int rowTotal = 0;
                for (int i = 0; i < DESIGNATION_COLUMNS.size(); i++) {
                    String col = DESIGNATION_COLUMNS.get(i);
                    long count = countsByDesignation.getOrDefault(col, 0L);
                    rowTotal += (int) count;
                    Cell cell = row.createCell(i + 1);
                    cell.setCellValue(count);
                    cell.setCellStyle(numStyle);
                }
                Cell totCell = row.createCell(DESIGNATION_COLUMNS.size() + 1);
                totCell.setCellValue(rowTotal);
                totCell.setCellStyle(numStyle);
            }

            // Auto-size columns
            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Daily Statement Excel report", e);
            throw new ReportGenerationException("daily_statement_excel",
                    "Failed to generate Daily Statement Excel report", e);
        }
    }

    private int getStrengthValue(String rowLabel, String designation,
                                  Map<String, Integer> sanctioned,
                                  Map<String, Integer> present,
                                  Map<String, Long> personnelCounts) {
        switch (rowLabel) {
            case "C.A.R SANCTIONED STRENGTH":
                return sanctioned.getOrDefault(designation, 0);
            case "PRESENT STRENGTH TOTAL":
                return present.getOrDefault(designation, 0);
            case "ACTUAL VACANCY":
                return sanctioned.getOrDefault(designation, 0) - present.getOrDefault(designation, 0);
            case "PRESENT STRENGTH PMT":
                return personnelCounts.getOrDefault(designation, 0L).intValue();
            case "PRESENT STRENGTH COMPANY":
                return 0;
            case "RECRUIT APC'S":
                return "APC".equals(designation) ? personnelCounts.getOrDefault("APC", 0L).intValue() : 0;
            default:
                return 0;
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

    private CellStyle createSectionHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
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
        style.setWrapText(true);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 8);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
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
}
