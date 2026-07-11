package com.policescheduler.report.generator;

import com.policescheduler.entity.Personnel;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.repository.PersonnelRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MtSectionExcelGenerator {

    private static final Logger log = LoggerFactory.getLogger(MtSectionExcelGenerator.class);

    private final PersonnelRepository personnelRepository;
    private final MtSectionReportGenerator mtSectionReportGenerator;

    public MtSectionExcelGenerator(PersonnelRepository personnelRepository,
                                    MtSectionReportGenerator mtSectionReportGenerator) {
        this.personnelRepository = personnelRepository;
        this.mtSectionReportGenerator = mtSectionReportGenerator;
    }

    private static final List<String> MT_SUB_SECTIONS = List.of(
            "COMPOL DUTY", "DK SP DUTY", "DCP'S DUTY",
            "CENTRAL SUBDIVISION", "SOUTH SUBDIVISION", "NORTH SUBDIVISION",
            "TRAFFIC SUBDIVISION", "CCB UNIT", "CCRB UNIT",
            "FINGER PRINT UNIT", "CEN UNIT", "CSB UNIT",
            "HIGHWAY PATROL DUTY", "HOYSALA DUTY", "CAR UNIT"
    );

    public byte[] generate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("MT Section");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle abstractStyle = createAbstractStyle(workbook);

            int rowIndex = 0;

            // Title row
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("CAR MT SECTION");
            titleCell.setCellStyle(headerStyle);

            // Date row
            Row dateRow = sheet.createRow(rowIndex++);
            dateRow.createCell(0).setCellValue("Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            rowIndex++; // blank row

            // Header row
            Row headerRow = sheet.createRow(rowIndex++);
            String[] headers = {"SL.NO", "NAME", "DESIGNATION N/RANK", "DUTY", "VEHICLE NO", "DEPLOYED FROM", "PDMS", "TYPE OF LICENCE"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Get personnel and group them
            List<Personnel> drivers = personnelRepository.findMtSectionPersonnel();

            Map<String, List<Personnel>> grouped = new LinkedHashMap<>();
            for (String subSection : MT_SUB_SECTIONS) {
                grouped.put(subSection, new ArrayList<>());
            }
            grouped.put("OTHER", new ArrayList<>());

            for (Personnel driver : drivers) {
                String section = mtSectionReportGenerator.categorizePersonnel(driver);
                grouped.get(section).add(driver);
            }

            // Data rows
            int globalSlNo = 1;
            for (Map.Entry<String, List<Personnel>> entry : grouped.entrySet()) {
                List<Personnel> sectionDrivers = entry.getValue();
                if (sectionDrivers.isEmpty()) continue;

                // Section header
                Row sectionRow = sheet.createRow(rowIndex++);
                Cell sectionCell = sectionRow.createCell(0);
                sectionCell.setCellValue(entry.getKey());
                sectionCell.setCellStyle(sectionStyle);

                for (Personnel driver : sectionDrivers) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(globalSlNo++);
                    row.createCell(1).setCellValue(driver.getPersonName() != null ? driver.getPersonName() : "");
                    row.createCell(2).setCellValue(driver.getDesignation() != null ? driver.getDesignation() : "");
                    row.createCell(3).setCellValue(driver.getDutyType() != null ? driver.getDutyType() : "");
                    row.createCell(4).setCellValue(driver.getVehicleNumber() != null ? driver.getVehicleNumber() : "-");
                    row.createCell(5).setCellValue(driver.getDeployedFrom() != null ? driver.getDeployedFrom() : "");
                    row.createCell(6).setCellValue(driver.getPdms() != null ? driver.getPdms() : "-");
                    row.createCell(7).setCellValue(driver.getLicenceType() != null ? driver.getLicenceType() : "-");

                    for (int i = 0; i < 8; i++) {
                        row.getCell(i).setCellStyle(dataStyle);
                    }
                }
            }

            rowIndex += 2; // Skip rows

            // Abstract
            Row abstractTitleRow = sheet.createRow(rowIndex++);
            Cell absTitleCell = abstractTitleRow.createCell(0);
            absTitleCell.setCellValue("ABSTRACT");
            absTitleCell.setCellStyle(headerStyle);

            long totalStrength = drivers.size();
            long pdmsDone = drivers.stream()
                    .filter(d -> d.getPdms() != null && d.getPdms().toUpperCase().contains("DONE")
                            && !d.getPdms().toUpperCase().contains("NOT"))
                    .count();
            long pdmsNotDone = totalStrength - pdmsDone;
            long lmvCount = drivers.stream()
                    .filter(d -> d.getLicenceType() != null && d.getLicenceType().toUpperCase().contains("LMV")
                            && !d.getLicenceType().toUpperCase().contains("HMV"))
                    .count();
            long lmvHmvCount = drivers.stream()
                    .filter(d -> d.getLicenceType() != null
                            && d.getLicenceType().toUpperCase().contains("LMV")
                            && d.getLicenceType().toUpperCase().contains("HMV"))
                    .count();

            addAbstractRow(sheet, rowIndex++, "TOTAL STRENGTH", totalStrength, abstractStyle);
            addAbstractRow(sheet, rowIndex++, "PDMS DONE", pdmsDone, abstractStyle);
            addAbstractRow(sheet, rowIndex++, "PDMS NOT DONE", pdmsNotDone, abstractStyle);
            addAbstractRow(sheet, rowIndex++, "LMV", lmvCount, abstractStyle);
            addAbstractRow(sheet, rowIndex++, "LMV & HMV", lmvHmvCount, abstractStyle);

            // Auto-size
            for (int i = 0; i < 8; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate MT Section Excel report", e);
            throw new ReportGenerationException("mt_section_excel", "Failed to generate MT Section Excel report", e);
        }
    }

    private void addAbstractRow(Sheet sheet, int rowIndex, String label, long value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(style);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(style);
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
        font.setFontHeightInPoints((short) 8);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
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

    private CellStyle createAbstractStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
