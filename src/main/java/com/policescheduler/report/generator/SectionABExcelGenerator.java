package com.policescheduler.report.generator;

import com.policescheduler.report.ReportDataService;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.report.dto.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class SectionABExcelGenerator {

    private static final Logger log = LoggerFactory.getLogger(SectionABExcelGenerator.class);

    private final ReportDataService reportDataService;

    public SectionABExcelGenerator(ReportDataService reportDataService) {
        this.reportDataService = reportDataService;
    }

    /**
     * Generates a combined Excel workbook with two sheets: "Section A" and "Section B".
     */
    public byte[] generateCombined() {
        try {
            SectionReportData dataA = reportDataService.buildSectionData("A");
            SectionReportData dataB = reportDataService.buildSectionData("B");

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                buildSheet(workbook, "Section A", dataA);
                buildSheet(workbook, "Section B", dataB);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                workbook.write(baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.error("Failed to generate combined Section A&B Excel report", e);
            throw new ReportGenerationException("section_ab_excel",
                    "Failed to generate combined Section A&B Excel report", e);
        }
    }

    private void buildSheet(XSSFWorkbook workbook, String sheetName, SectionReportData data) {
        Sheet sheet = workbook.createSheet(sheetName);

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle boldStyle = createBoldStyle(workbook);
        CellStyle totalStyle = createTotalStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);

        int rowIndex = 0;

        // Header row
        Row headerRow = sheet.createRow(rowIndex++);
        String[] headers = {data.sectionLabel(), "", "", "DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int dataStartRow = rowIndex;

        // Data rows
        for (DutyCategoryRow catRow : data.rows()) {
            if (catRow.subRows().isEmpty()) {
                Row row = sheet.createRow(rowIndex++);
                fillDataRow(row, catRow.serialNumber(), catRow.categoryName(),
                        formatPersonnelList(catRow.personnel()), catRow.designationCounts(), dataStyle, numberStyle);
            } else {
                if (catRow.personnel().isEmpty()) {
                    Row row = sheet.createRow(rowIndex++);
                    fillDataRow(row, catRow.serialNumber(), catRow.categoryName(),
                            "", catRow.designationCounts(), boldStyle, numberStyle);
                } else {
                    Row row = sheet.createRow(rowIndex++);
                    fillDataRow(row, catRow.serialNumber(), catRow.categoryName(),
                            formatPersonnelList(catRow.personnel()), catRow.designationCounts(), boldStyle, numberStyle);
                }

                for (SubDutyRow sub : catRow.subRows()) {
                    Row row = sheet.createRow(rowIndex++);
                    fillDataRow(row, 0, "  " + sub.subCategoryName(),
                            formatPersonnelList(sub.personnel()), sub.designationCounts(), dataStyle, numberStyle);
                }
            }
        }

        // TOTAL row
        Row totalRow = sheet.createRow(rowIndex);
        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellStyle(totalStyle);

        Cell totalLabel2 = totalRow.createCell(1);
        totalLabel2.setCellValue("TOTAL=");
        totalLabel2.setCellStyle(totalStyle);

        Cell totalLabel3 = totalRow.createCell(2);
        totalLabel3.setCellStyle(totalStyle);

        for (int col = 3; col < 10; col++) {
            Cell cell = totalRow.createCell(col);
            String colLetter = String.valueOf((char) ('A' + col));
            String formula = String.format("SUM(%s%d:%s%d)", colLetter, dataStartRow + 1, colLetter, rowIndex);
            cell.setCellFormula(formula);
            cell.setCellStyle(totalStyle);
        }

        rowIndex += 3;

        // Abstract table
        Row abstractHeaderRow = sheet.createRow(rowIndex++);
        String[] abstractHeaders = {"SL NO", "ABSTRACT", "", "DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC"};
        for (int i = 0; i < abstractHeaders.length; i++) {
            Cell cell = abstractHeaderRow.createCell(i);
            cell.setCellValue(abstractHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        Row abstractDataRow = sheet.createRow(rowIndex++);
        Cell absSlCell = abstractDataRow.createCell(0);
        absSlCell.setCellValue(1);
        absSlCell.setCellStyle(numberStyle);

        Cell absSectionCell = abstractDataRow.createCell(1);
        absSectionCell.setCellValue(data.sectionLabel());
        absSectionCell.setCellStyle(boldStyle);

        Cell absEmptyCell = abstractDataRow.createCell(2);
        absEmptyCell.setCellStyle(dataStyle);

        for (int i = 0; i < data.designationTotals().length; i++) {
            Cell cell = abstractDataRow.createCell(3 + i);
            cell.setCellValue(data.designationTotals()[i]);
            cell.setCellStyle(numberStyle);
        }

        Row abstractTotalRow = sheet.createRow(rowIndex);
        Cell absTotalSl = abstractTotalRow.createCell(0);
        absTotalSl.setCellStyle(totalStyle);

        Cell absTotalLabel = abstractTotalRow.createCell(1);
        absTotalLabel.setCellValue("TOTAL=");
        absTotalLabel.setCellStyle(totalStyle);

        Cell absTotalEmpty = abstractTotalRow.createCell(2);
        absTotalEmpty.setCellStyle(totalStyle);

        for (int i = 0; i < data.designationTotals().length; i++) {
            Cell cell = abstractTotalRow.createCell(3 + i);
            cell.setCellValue(data.designationTotals()[i]);
            cell.setCellStyle(totalStyle);
        }

        // Auto-size columns
        for (int i = 0; i < 10; i++) {
            sheet.autoSizeColumn(i);
        }
        for (int i = 3; i < 10; i++) {
            if (sheet.getColumnWidth(i) < 2000) {
                sheet.setColumnWidth(i, 2000);
            }
        }
    }

    public byte[] generate(String section) {
        try {
            SectionReportData data = reportDataService.buildSectionData(section);

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Section " + section);

                // Create styles (matching PDF: gray header, gray total, black text)
                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle dataStyle = createDataStyle(workbook);
                CellStyle boldStyle = createBoldStyle(workbook);
                CellStyle totalStyle = createTotalStyle(workbook);
                CellStyle numberStyle = createNumberStyle(workbook);

                int rowIndex = 0;

                // Header row: SECTION A/B title + designation columns
                Row headerRow = sheet.createRow(rowIndex++);
                String[] headers = {data.sectionLabel(), "", "", "DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                int dataStartRow = rowIndex;

                // Data rows
                for (DutyCategoryRow catRow : data.rows()) {
                    if (catRow.subRows().isEmpty()) {
                        Row row = sheet.createRow(rowIndex++);
                        fillDataRow(row, catRow.serialNumber(), catRow.categoryName(),
                                formatPersonnelList(catRow.personnel()), catRow.designationCounts(), dataStyle, numberStyle);
                    } else {
                        // Category with sub-rows
                        if (catRow.personnel().isEmpty()) {
                            Row row = sheet.createRow(rowIndex++);
                            fillDataRow(row, catRow.serialNumber(), catRow.categoryName(),
                                    "", catRow.designationCounts(), boldStyle, numberStyle);
                        } else {
                            Row row = sheet.createRow(rowIndex++);
                            fillDataRow(row, catRow.serialNumber(), catRow.categoryName(),
                                    formatPersonnelList(catRow.personnel()), catRow.designationCounts(), boldStyle, numberStyle);
                        }

                        // Sub-rows
                        for (SubDutyRow sub : catRow.subRows()) {
                            Row row = sheet.createRow(rowIndex++);
                            fillDataRow(row, 0, "  " + sub.subCategoryName(),
                                    formatPersonnelList(sub.personnel()), sub.designationCounts(), dataStyle, numberStyle);
                        }
                    }
                }

                // TOTAL row with SUM formulas
                Row totalRow = sheet.createRow(rowIndex);
                Cell totalLabelCell = totalRow.createCell(0);
                totalLabelCell.setCellStyle(totalStyle);

                Cell totalLabel2 = totalRow.createCell(1);
                totalLabel2.setCellValue("TOTAL=");
                totalLabel2.setCellStyle(totalStyle);

                Cell totalLabel3 = totalRow.createCell(2);
                totalLabel3.setCellStyle(totalStyle);

                for (int col = 3; col < 10; col++) {
                    Cell cell = totalRow.createCell(col);
                    String colLetter = String.valueOf((char) ('A' + col));
                    String formula = String.format("SUM(%s%d:%s%d)", colLetter, dataStartRow + 1, colLetter, rowIndex);
                    cell.setCellFormula(formula);
                    cell.setCellStyle(totalStyle);
                }

                rowIndex += 3; // Skip rows before abstract

                // Abstract table
                Row abstractHeaderRow = sheet.createRow(rowIndex++);
                String[] abstractHeaders = {"SL NO", "ABSTRACT", "", "DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC"};
                for (int i = 0; i < abstractHeaders.length; i++) {
                    Cell cell = abstractHeaderRow.createCell(i);
                    cell.setCellValue(abstractHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Abstract data row
                Row abstractDataRow = sheet.createRow(rowIndex++);
                Cell absSlCell = abstractDataRow.createCell(0);
                absSlCell.setCellValue(1);
                absSlCell.setCellStyle(numberStyle);

                Cell absSectionCell = abstractDataRow.createCell(1);
                absSectionCell.setCellValue(data.sectionLabel());
                absSectionCell.setCellStyle(boldStyle);

                Cell absEmptyCell = abstractDataRow.createCell(2);
                absEmptyCell.setCellStyle(dataStyle);

                for (int i = 0; i < data.designationTotals().length; i++) {
                    Cell cell = abstractDataRow.createCell(3 + i);
                    cell.setCellValue(data.designationTotals()[i]);
                    cell.setCellStyle(numberStyle);
                }

                // Abstract TOTAL row
                Row abstractTotalRow = sheet.createRow(rowIndex);
                Cell absTotalSl = abstractTotalRow.createCell(0);
                absTotalSl.setCellStyle(totalStyle);

                Cell absTotalLabel = abstractTotalRow.createCell(1);
                absTotalLabel.setCellValue("TOTAL=");
                absTotalLabel.setCellStyle(totalStyle);

                Cell absTotalEmpty = abstractTotalRow.createCell(2);
                absTotalEmpty.setCellStyle(totalStyle);

                for (int i = 0; i < data.designationTotals().length; i++) {
                    Cell cell = abstractTotalRow.createCell(3 + i);
                    cell.setCellValue(data.designationTotals()[i]);
                    cell.setCellStyle(totalStyle);
                }

                // Auto-size columns
                for (int i = 0; i < 10; i++) {
                    sheet.autoSizeColumn(i);
                }
                // Set minimum widths for designation columns
                for (int i = 3; i < 10; i++) {
                    if (sheet.getColumnWidth(i) < 2000) {
                        sheet.setColumnWidth(i, 2000);
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                workbook.write(baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.error("Failed to generate Section {} Excel report", section, e);
            throw new ReportGenerationException("section_ab_excel",
                    "Failed to generate Section " + section + " Excel report", e);
        }
    }

    private void fillDataRow(Row row, int slNo, String category, String personnel, int[] counts, CellStyle textStyle, CellStyle numStyle) {
        Cell slCell = row.createCell(0);
        if (slNo > 0) slCell.setCellValue(slNo);
        slCell.setCellStyle(textStyle);

        Cell catCell = row.createCell(1);
        catCell.setCellValue(category);
        catCell.setCellStyle(textStyle);

        Cell persCell = row.createCell(2);
        persCell.setCellValue(personnel);
        persCell.setCellStyle(textStyle);

        for (int i = 0; i < counts.length; i++) {
            Cell cell = row.createCell(3 + i);
            cell.setCellValue(counts[i]);
            cell.setCellStyle(numStyle);
        }
    }

    /**
     * Formats personnel list with grouped badge IDs — matching the PDF format.
     * Names first, then badge IDs grouped by designation: "AHC-2733, 2751, APC-149, 255"
     */
    private String formatPersonnelList(List<PersonnelEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";

        List<String> names = new ArrayList<>();
        Map<String, List<String>> badgeIdsByDesignation = new LinkedHashMap<>();

        for (PersonnelEntry entry : entries) {
            String display = entry.displayName();
            if (display == null || display.isEmpty()) continue;

            if (display.matches(".*\\d+.*") && entry.designation() != null) {
                String desig = entry.designation().toUpperCase().trim();
                badgeIdsByDesignation.computeIfAbsent(desig, k -> new ArrayList<>());
                String numPart = display.replaceAll("^[A-Za-z]+-?", "");
                badgeIdsByDesignation.get(desig).add(numPart);
            } else {
                names.add(display);
            }
        }

        StringBuilder result = new StringBuilder();

        if (!names.isEmpty()) {
            result.append(String.join(", ", names));
        }

        for (Map.Entry<String, List<String>> group : badgeIdsByDesignation.entrySet()) {
            if (result.length() > 0) result.append(", ");
            String prefix = group.getKey();
            List<String> ids = group.getValue();
            result.append(prefix).append("-").append(ids.get(0));
            for (int i = 1; i < ids.size(); i++) {
                result.append(", ").append(ids.get(i));
            }
        }

        return result.toString();
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        // Light gray background matching PDF
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
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
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 8);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        // Light gray background matching PDF total row
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 8);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
