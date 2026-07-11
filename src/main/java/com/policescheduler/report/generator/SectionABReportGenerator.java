package com.policescheduler.report.generator;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.policescheduler.report.PdfReportGenerator;
import com.policescheduler.report.PdfStyleHelper;
import com.policescheduler.report.ReportDataService;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.report.ReportLocalizationService;
import com.policescheduler.report.ReportRequest;
import com.policescheduler.report.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SectionABReportGenerator implements PdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(SectionABReportGenerator.class);

    private static final List<String> DESIGNATION_COLUMNS =
            List.of("DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC");

    private static final Color HEADER_BG = new Color(211, 211, 211);  // Light gray
    private static final Color TOTAL_BG = new Color(211, 211, 211);   // Light gray
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);
    private static final Font CELL_BOLD_FONT = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);

    private final ReportDataService reportDataService;
    private final PdfStyleHelper pdfStyleHelper;
    private final ReportLocalizationService localizationService;

    public SectionABReportGenerator(ReportDataService reportDataService,
                                     PdfStyleHelper pdfStyleHelper,
                                     ReportLocalizationService localizationService) {
        this.reportDataService = reportDataService;
        this.pdfStyleHelper = pdfStyleHelper;
        this.localizationService = localizationService;
    }

    @Override
    public String getReportType() {
        return "section_ab";
    }

    @Override
    public byte[] generate(ReportRequest request) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate(), 15, 15, 15, 15);
            PdfWriter.getInstance(document, baos);
            document.open();

            String locale = request.getLocale() != null ? request.getLocale() : "en";

            if (request.isCombined()) {
                // Combined A + B report
                SectionReportData dataA = reportDataService.buildSectionData("A");
                addSectionToDocument(document, dataA);

                document.newPage();

                SectionReportData dataB = reportDataService.buildSectionData("B");
                addSectionToDocument(document, dataB);
            } else {
                String section = request.getSection() != null ? request.getSection() : "A";
                SectionReportData data = reportDataService.buildSectionData(section);
                addSectionToDocument(document, data);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate section A/B report", e);
            throw new ReportGenerationException("section_ab",
                    "Failed to generate section A/B report", e);
        }
    }

    private void addSectionToDocument(Document document, SectionReportData data) throws DocumentException {
        // Column widths: SL(3%), Category(20%), Personnel(37%), DCP(5%), ACP(5%), RPI(5%), RSI(5%), ARSI(5%), AHC(5%), APC(5%)
        float[] columnWidths = {3f, 20f, 37f, 5f, 5f, 5f, 5f, 5f, 5f, 5f};
        PdfPTable table = new PdfPTable(columnWidths.length);
        table.setWidthPercentage(100);
        table.setWidths(columnWidths);

        // Header row: Section title + designation columns
        PdfPCell titleCell = new PdfPCell(new Phrase(data.sectionLabel(), HEADER_FONT));
        titleCell.setColspan(3);
        titleCell.setBackgroundColor(HEADER_BG);
        titleCell.setPadding(3);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(titleCell);

        for (String col : DESIGNATION_COLUMNS) {
            PdfPCell cell = new PdfPCell(new Phrase(col, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(2);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }

        // Data rows
        for (DutyCategoryRow row : data.rows()) {
            if (row.subRows().isEmpty()) {
                // Simple row — no sub-categories
                addDataRow(table, String.valueOf(row.serialNumber()), row.categoryName(),
                        formatPersonnelList(row.personnel()), row.designationCounts(), false);
            } else {
                // Category with sub-rows
                if (row.personnel().isEmpty()) {
                    // Main row just shows category name, no personnel
                    addCategoryHeaderRow(table, String.valueOf(row.serialNumber()), row.categoryName(), row.designationCounts());
                } else {
                    addDataRow(table, String.valueOf(row.serialNumber()), row.categoryName(),
                            formatPersonnelList(row.personnel()), row.designationCounts(), true);
                }

                // Then add each sub-row
                for (SubDutyRow sub : row.subRows()) {
                    addSubRow(table, sub.subCategoryName(), formatPersonnelList(sub.personnel()), sub.designationCounts());
                }
            }
        }

        // TOTAL row
        PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL=", TOTAL_FONT));
        totalLabelCell.setColspan(3);
        totalLabelCell.setBackgroundColor(TOTAL_BG);
        totalLabelCell.setPadding(3);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalLabelCell);

        for (int count : data.designationTotals()) {
            PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(count), TOTAL_FONT));
            cell.setBackgroundColor(TOTAL_BG);
            cell.setPadding(2);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        document.add(table);

        // Add spacing before abstract
        document.add(new Paragraph("\n"));

        // Abstract table
        addAbstractTable(document, data);
    }

    private void addAbstractTable(Document document, SectionReportData data) throws DocumentException {
        // Column widths: SL NO(8%), ABSTRACT(42%), DCP(7%), ACP(7%), RPI(7%), RSI(7%), ARSI(7%), AHC(7%), APC(7%)
        float[] abstractWidths = {8f, 42f, 7f, 7f, 7f, 7f, 7f, 7f, 7f};
        PdfPTable abstractTable = new PdfPTable(abstractWidths.length);
        abstractTable.setWidthPercentage(60);
        abstractTable.setWidths(abstractWidths);
        abstractTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        // Header row
        PdfPCell slHeader = new PdfPCell(new Phrase("SL\nNO", HEADER_FONT));
        slHeader.setBackgroundColor(HEADER_BG);
        slHeader.setPadding(4);
        slHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        slHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
        abstractTable.addCell(slHeader);

        PdfPCell abstractHeader = new PdfPCell(new Phrase("ABSTRACT", HEADER_FONT));
        abstractHeader.setBackgroundColor(HEADER_BG);
        abstractHeader.setPadding(4);
        abstractHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        abstractHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
        abstractTable.addCell(abstractHeader);

        for (String col : DESIGNATION_COLUMNS) {
            PdfPCell cell = new PdfPCell(new Phrase(col, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(4);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            abstractTable.addCell(cell);
        }

        // Data row — the section totals
        PdfPCell slCell = new PdfPCell(new Phrase("1", CELL_BOLD_FONT));
        slCell.setPadding(4);
        slCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        abstractTable.addCell(slCell);

        PdfPCell sectionCell = new PdfPCell(new Phrase(data.sectionLabel(), CELL_BOLD_FONT));
        sectionCell.setPadding(4);
        abstractTable.addCell(sectionCell);

        for (int count : data.designationTotals()) {
            PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(count), CELL_FONT));
            cell.setPadding(4);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            abstractTable.addCell(cell);
        }

        // TOTAL row
        PdfPCell totalSlCell = new PdfPCell(new Phrase("", TOTAL_FONT));
        totalSlCell.setBackgroundColor(TOTAL_BG);
        totalSlCell.setPadding(4);
        abstractTable.addCell(totalSlCell);

        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL=", TOTAL_FONT));
        totalLabel.setBackgroundColor(TOTAL_BG);
        totalLabel.setPadding(4);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        abstractTable.addCell(totalLabel);

        for (int count : data.designationTotals()) {
            PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(count), TOTAL_FONT));
            cell.setBackgroundColor(TOTAL_BG);
            cell.setPadding(4);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            abstractTable.addCell(cell);
        }

        document.add(abstractTable);
    }

    private void addDataRow(PdfPTable table, String slNo, String category, String personnel, int[] counts, boolean bold) {
        Font catFont = bold ? CELL_BOLD_FONT : CELL_FONT;

        PdfPCell slCell = new PdfPCell(new Phrase(slNo, CELL_FONT));
        slCell.setPadding(2);
        slCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(slCell);

        PdfPCell catCell = new PdfPCell(new Phrase(category, catFont));
        catCell.setPadding(2);
        table.addCell(catCell);

        PdfPCell persCell = new PdfPCell(new Phrase(personnel, CELL_FONT));
        persCell.setPadding(2);
        table.addCell(persCell);

        for (int count : counts) {
            PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(count), CELL_FONT));
            cell.setPadding(2);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addCategoryHeaderRow(PdfPTable table, String slNo, String category, int[] counts) {
        PdfPCell slCell = new PdfPCell(new Phrase(slNo, CELL_BOLD_FONT));
        slCell.setPadding(2);
        slCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(slCell);

        PdfPCell catCell = new PdfPCell(new Phrase(category, CELL_BOLD_FONT));
        catCell.setPadding(2);
        table.addCell(catCell);

        PdfPCell persCell = new PdfPCell(new Phrase("", CELL_FONT));
        persCell.setPadding(2);
        table.addCell(persCell);

        for (int count : counts) {
            PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(count), CELL_FONT));
            cell.setPadding(2);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addSubRow(PdfPTable table, String subCategory, String personnel, int[] counts) {
        PdfPCell slCell = new PdfPCell(new Phrase("", CELL_FONT));
        slCell.setPadding(2);
        table.addCell(slCell);

        PdfPCell catCell = new PdfPCell(new Phrase("  " + subCategory, CELL_FONT));
        catCell.setPadding(2);
        table.addCell(catCell);

        PdfPCell persCell = new PdfPCell(new Phrase(personnel, CELL_FONT));
        persCell.setPadding(2);
        table.addCell(persCell);

        for (int count : counts) {
            PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(count), CELL_FONT));
            cell.setPadding(2);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private String formatPersonnelList(List<PersonnelEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";

        // Group entries: names first, then badge IDs grouped by designation
        List<String> names = new ArrayList<>();
        Map<String, List<String>> badgeIdsByDesignation = new LinkedHashMap<>();

        for (PersonnelEntry entry : entries) {
            String display = entry.displayName();
            if (display == null || display.isEmpty()) continue;

            // Check if it's a badge ID (contains digits and dashes like "APC-2658")
            if (display.matches(".*\\d+.*") && entry.designation() != null) {
                String desig = entry.designation().toUpperCase().trim();
                badgeIdsByDesignation.computeIfAbsent(desig, k -> new ArrayList<>());
                // Extract just the number part from badge ID
                String numPart = display.replaceAll("^[A-Za-z]+-?", "");
                badgeIdsByDesignation.get(desig).add(numPart);
            } else {
                // It's a name
                names.add(display);
            }
        }

        StringBuilder result = new StringBuilder();

        // Add names first
        if (!names.isEmpty()) {
            result.append(String.join(", ", names));
        }

        // Add grouped badge IDs: "AHC-2733, 2751, 2601, APC-149, 255, 287"
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
}
