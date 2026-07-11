package com.policescheduler.report.generator;

import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.Platoon;
import com.policescheduler.entity.PlatoonRotationState;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.PlatoonRepository;
import com.policescheduler.repository.PlatoonRotationStateRepository;
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
public class PlatoonChartExcelGenerator {

    private static final Logger log = LoggerFactory.getLogger(PlatoonChartExcelGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final int NUM_ROTATION_PERIODS = 5;
    private static final int ROTATION_DAYS = 15;

    private static final List<String> DUTY_COLUMNS = List.of(
            "GUARD-I", "GUARD-II", "CHECK POINT", "PRISON/VIP ESCORT/OUT", "STRIKING FORCE/HELP"
    );

    private static final List<String> ALL_SECTIONS = List.of("C", "D", "E", "F", "G");

    private final PersonnelRepository personnelRepository;
    private final PlatoonRepository platoonRepository;
    private final PlatoonRotationStateRepository rotationStateRepository;

    public PlatoonChartExcelGenerator(PersonnelRepository personnelRepository,
                                       PlatoonRepository platoonRepository,
                                       PlatoonRotationStateRepository rotationStateRepository) {
        this.personnelRepository = personnelRepository;
        this.platoonRepository = platoonRepository;
        this.rotationStateRepository = rotationStateRepository;
    }

    public byte[] generate() {
        try {
            PlatoonRotationState state = rotationStateRepository.findAll().stream()
                    .findFirst().orElse(null);
            List<Platoon> platoons = platoonRepository.findAllByOrderByBaseOffsetAsc();
            List<Personnel> allPersonnel = personnelRepository.findAll().stream()
                    .filter(p -> p.getIsActive() != null && p.getIsActive())
                    .filter(p -> p.getPlatoonId() != null)
                    .filter(p -> ALL_SECTIONS.contains(p.getSection()))
                    .collect(Collectors.toList());

            int currentCycle = state != null ? state.getCurrentCycleIndex() : 0;
            LocalDate anchorStart = state != null && state.getLastRotationDate() != null
                    ? state.getLastRotationDate() : LocalDate.now();

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Section C");

                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle dataStyle = createDataStyle(workbook);
                CellStyle dateStyle = createDateStyle(workbook);

                int rowIndex = 0;

                // Title row
                Row titleRow = sheet.createRow(rowIndex++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue("SECTION C");
                titleCell.setCellStyle(headerStyle);
                for (int i = 1; i <= 5; i++) {
                    titleRow.createCell(i).setCellStyle(headerStyle);
                }

                // Header row with duty columns and strength
                Row headerRow = sheet.createRow(rowIndex++);
                Cell dateHeaderCell = headerRow.createCell(0);
                dateHeaderCell.setCellValue("DATE");
                dateHeaderCell.setCellStyle(headerStyle);

                for (int dutyIdx = 0; dutyIdx < DUTY_COLUMNS.size(); dutyIdx++) {
                    Long platoonAtDuty = null;
                    for (Platoon p : platoons) {
                        int idx = (p.getBaseOffset() + currentCycle) % 5;
                        if (idx == dutyIdx) {
                            platoonAtDuty = p.getId();
                            break;
                        }
                    }
                    long ahc = 0, apc = 0;
                    if (platoonAtDuty != null) {
                        Long pid = platoonAtDuty;
                        ahc = allPersonnel.stream()
                                .filter(per -> pid.equals(per.getPlatoonId()) && "AHC".equals(per.getDesignation()))
                                .count();
                        apc = allPersonnel.stream()
                                .filter(per -> pid.equals(per.getPlatoonId()) && "APC".equals(per.getDesignation()))
                                .count();
                    }
                    String headerText = DUTY_COLUMNS.get(dutyIdx) + "\n" + ahc + " AHC, " + apc + " APC, TOTAL=" + (ahc + apc);
                    Cell cell = headerRow.createCell(dutyIdx + 1);
                    cell.setCellValue(headerText);
                    cell.setCellStyle(headerStyle);
                }

                // Data rows
                for (int period = 0; period < NUM_ROTATION_PERIODS; period++) {
                    int cycleForPeriod = currentCycle + period;
                    LocalDate periodStart = anchorStart.plusDays((long) period * ROTATION_DAYS);
                    LocalDate periodEnd = periodStart.plusDays(ROTATION_DAYS - 1);

                    Row dataRow = sheet.createRow(rowIndex++);

                    Cell dateCellVal = dataRow.createCell(0);
                    dateCellVal.setCellValue(periodStart.format(DATE_FORMAT) + " TO " + periodEnd.format(DATE_FORMAT));
                    dateCellVal.setCellStyle(dateStyle);

                    for (int dutyIdx = 0; dutyIdx < DUTY_COLUMNS.size(); dutyIdx++) {
                        StringBuilder cellContent = new StringBuilder();
                        for (Platoon p : platoons) {
                            int idx = (p.getBaseOffset() + cycleForPeriod) % 5;
                            if (idx == dutyIdx) {
                                cellContent.append("PLATOON-").append(p.getName().replace("Platoon ", "")).append("\n");
                                List<Personnel> platoonPersonnel = allPersonnel.stream()
                                        .filter(per -> p.getId().equals(per.getPlatoonId()))
                                        .collect(Collectors.toList());
                                if (!platoonPersonnel.isEmpty()) {
                                    cellContent.append(formatBadgeIds(platoonPersonnel));
                                }
                                break;
                            }
                        }
                        Cell cell = dataRow.createCell(dutyIdx + 1);
                        cell.setCellValue(cellContent.toString());
                        cell.setCellStyle(dataStyle);
                    }
                }

                // Auto-size columns
                for (int i = 0; i <= 5; i++) {
                    sheet.autoSizeColumn(i);
                }
                sheet.setColumnWidth(0, 5000);
                for (int i = 1; i <= 5; i++) {
                    if (sheet.getColumnWidth(i) < 8000) {
                        sheet.setColumnWidth(i, 8000);
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                workbook.write(baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.error("Failed to generate Section C Excel report", e);
            throw new ReportGenerationException("platoon_chart_excel", "Failed to generate Section C Excel report", e);
        }
    }

    private String formatBadgeIds(List<Personnel> personnel) {
        StringBuilder sb = new StringBuilder();
        List<Personnel> ahcList = personnel.stream()
                .filter(p -> "AHC".equals(p.getDesignation()))
                .collect(Collectors.toList());
        List<Personnel> apcList = personnel.stream()
                .filter(p -> "APC".equals(p.getDesignation()))
                .collect(Collectors.toList());

        if (!ahcList.isEmpty()) {
            sb.append("AHC-");
            sb.append(ahcList.stream()
                    .map(p -> extractBadgeNumber(p.getBadgeId()))
                    .collect(Collectors.joining(", ")));
        }
        if (!apcList.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("APC-");
            sb.append(apcList.stream()
                    .map(p -> extractBadgeNumber(p.getBadgeId()))
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    private String extractBadgeNumber(String badgeId) {
        if (badgeId != null && badgeId.contains("-")) {
            return badgeId.substring(badgeId.lastIndexOf('-') + 1);
        }
        return badgeId != null ? badgeId : "";
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
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
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
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 8);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
