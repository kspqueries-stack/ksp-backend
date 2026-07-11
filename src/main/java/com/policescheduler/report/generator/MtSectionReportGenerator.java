package com.policescheduler.report.generator;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.policescheduler.entity.Personnel;
import com.policescheduler.report.PdfReportGenerator;
import com.policescheduler.report.PdfStyleHelper;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.report.ReportLocalizationService;
import com.policescheduler.report.ReportRequest;
import com.policescheduler.repository.PersonnelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

@Service
public class MtSectionReportGenerator implements PdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(MtSectionReportGenerator.class);

    private static final Color HEADER_BG = new Color(211, 211, 211);
    private static final Color SECTION_BG = new Color(230, 230, 230);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);
    private static final Font ABSTRACT_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);

    private static final List<String> MT_SUB_SECTIONS = List.of(
            "COMPOL DUTY", "DK SP DUTY", "DCP'S DUTY",
            "CENTRAL SUBDIVISION", "SOUTH SUBDIVISION", "NORTH SUBDIVISION",
            "TRAFFIC SUBDIVISION", "CCB UNIT", "CCRB UNIT",
            "FINGER PRINT UNIT", "CEN UNIT", "CSB UNIT",
            "HIGHWAY PATROL DUTY", "HOYSALA DUTY", "CAR UNIT"
    );

    private final PersonnelRepository personnelRepository;
    private final PdfStyleHelper pdfStyleHelper;
    private final ReportLocalizationService localizationService;

    public MtSectionReportGenerator(PersonnelRepository personnelRepository,
                                     PdfStyleHelper pdfStyleHelper,
                                     ReportLocalizationService localizationService) {
        this.personnelRepository = personnelRepository;
        this.pdfStyleHelper = pdfStyleHelper;
        this.localizationService = localizationService;
    }

    @Override
    public String getReportType() {
        return "mt_section";
    }

    @Override
    public byte[] generate(ReportRequest request) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 15, 15, 15, 15);
            PdfWriter.getInstance(document, baos);
            document.open();

            String locale = request.getLocale() != null ? request.getLocale() : "en";
            LocalDate reportDate = request.getDate() != null ? request.getDate() : LocalDate.now();

            // Title
            Paragraph title = new Paragraph("CAR MT SECTION", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            document.add(title);

            // Date
            Paragraph datePara = new Paragraph("Date: " + reportDate.toString(), CELL_FONT);
            datePara.setAlignment(Element.ALIGN_RIGHT);
            datePara.setSpacingAfter(10);
            document.add(datePara);

            // Query ALL MT section personnel (not just those with vehicle numbers)
            List<Personnel> drivers = personnelRepository.findMtSectionPersonnel();

            // Group by duty type / duty location into sections
            Map<String, List<Personnel>> grouped = new LinkedHashMap<>();
            for (String subSection : MT_SUB_SECTIONS) {
                grouped.put(subSection, new ArrayList<>());
            }
            grouped.put("OTHER", new ArrayList<>());

            for (Personnel driver : drivers) {
                String section = categorizePersonnel(driver);
                grouped.get(section).add(driver);
            }

            // Build table: 8 columns
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{5, 18, 10, 17, 12, 14, 10, 10});

            // Header row
            String[] headers = {"SL.NO", "NAME", "DESIGNATION\nN/RANK", "DUTY", "VEHICLE NO", "DEPLOYED FROM", "PDMS", "TYPE OF\nLICENCE"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
                cell.setBackgroundColor(HEADER_BG);
                cell.setPadding(3);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setBorderWidth(0.5f);
                table.addCell(cell);
            }

            // Data rows grouped by section
            int globalSlNo = 1;
            for (Map.Entry<String, List<Personnel>> entry : grouped.entrySet()) {
                List<Personnel> sectionDrivers = entry.getValue();
                if (sectionDrivers.isEmpty()) continue;

                // Section header row
                PdfPCell sectionHeader = new PdfPCell(new Phrase(entry.getKey(), SECTION_FONT));
                sectionHeader.setColspan(8);
                sectionHeader.setBackgroundColor(SECTION_BG);
                sectionHeader.setPadding(3);
                sectionHeader.setHorizontalAlignment(Element.ALIGN_LEFT);
                sectionHeader.setBorderWidth(0.5f);
                table.addCell(sectionHeader);

                for (Personnel driver : sectionDrivers) {
                    addDataRow(table, globalSlNo++, driver);
                }
            }

            document.add(table);

            // Abstract summary
            document.add(new Paragraph("\n"));
            Paragraph abstractTitle = new Paragraph("ABSTRACT", ABSTRACT_FONT);
            abstractTitle.setAlignment(Element.ALIGN_CENTER);
            abstractTitle.setSpacingAfter(5);
            document.add(abstractTitle);

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

            PdfPTable abstractTable = new PdfPTable(2);
            abstractTable.setWidthPercentage(40);
            abstractTable.setHorizontalAlignment(Element.ALIGN_CENTER);

            addAbstractRow(abstractTable, "1", "TOTAL STRENGTH", String.valueOf(totalStrength));
            addAbstractRow(abstractTable, "2", "PDMS DONE", String.valueOf(pdmsDone));
            addAbstractRow(abstractTable, "3", "PDMS NOT DONE", String.valueOf(pdmsNotDone));
            addAbstractRow(abstractTable, "4", "LMV", String.valueOf(lmvCount));
            addAbstractRow(abstractTable, "5", "LMV & HMV", String.valueOf(lmvHmvCount));

            document.add(abstractTable);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate MT section report", e);
            throw new ReportGenerationException("mt_section", "Failed to generate MT section report", e);
        }
    }

    private void addDataRow(PdfPTable table, int slNo, Personnel driver) {
        PdfPCell slCell = new PdfPCell(new Phrase(String.valueOf(slNo), CELL_FONT));
        slCell.setPadding(3);
        slCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        slCell.setBorderWidth(0.5f);
        table.addCell(slCell);

        PdfPCell nameCell = new PdfPCell(new Phrase(
                driver.getPersonName() != null ? driver.getPersonName() : "", CELL_FONT));
        nameCell.setPadding(3);
        nameCell.setBorderWidth(0.5f);
        table.addCell(nameCell);

        PdfPCell desigCell = new PdfPCell(new Phrase(
                driver.getDesignation() != null ? driver.getDesignation() : "", CELL_FONT));
        desigCell.setPadding(3);
        desigCell.setBorderWidth(0.5f);
        table.addCell(desigCell);

        PdfPCell dutyCell = new PdfPCell(new Phrase(
                driver.getDutyType() != null ? driver.getDutyType() : "", CELL_FONT));
        dutyCell.setPadding(3);
        dutyCell.setBorderWidth(0.5f);
        table.addCell(dutyCell);

        PdfPCell vehicleCell = new PdfPCell(new Phrase(
                driver.getVehicleNumber() != null ? driver.getVehicleNumber() : "-", CELL_FONT));
        vehicleCell.setPadding(3);
        vehicleCell.setBorderWidth(0.5f);
        table.addCell(vehicleCell);

        PdfPCell deployedCell = new PdfPCell(new Phrase(
                driver.getDeployedFrom() != null ? driver.getDeployedFrom() : "", CELL_FONT));
        deployedCell.setPadding(3);
        deployedCell.setBorderWidth(0.5f);
        table.addCell(deployedCell);

        PdfPCell pdmsCell = new PdfPCell(new Phrase(
                driver.getPdms() != null ? driver.getPdms() : "-", CELL_FONT));
        pdmsCell.setPadding(3);
        pdmsCell.setBorderWidth(0.5f);
        table.addCell(pdmsCell);

        PdfPCell licenceCell = new PdfPCell(new Phrase(
                driver.getLicenceType() != null ? driver.getLicenceType() : "-", CELL_FONT));
        licenceCell.setPadding(3);
        licenceCell.setBorderWidth(0.5f);
        table.addCell(licenceCell);
    }

    private void addAbstractRow(PdfPTable table, String slNo, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(slNo + "  " + label, SECTION_FONT));
        labelCell.setPadding(4);
        labelCell.setBorderWidth(0.5f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, SECTION_FONT));
        valueCell.setPadding(4);
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valueCell.setBorderWidth(0.5f);
        table.addCell(valueCell);
    }

    // --- Section categorization logic based on duty type title and location ---

    private static final Set<String> CENTRAL_STATIONS = Set.of(
            "NORTH PS", "SOUTH PS", "WOMEN PS", "EAST PS", "BARKE PS", "ACP CENTRAL",
            "CENTRAL SUB DIV", "PI NORTH PS", "PI SOUTH PS", "PI WOMEN PS", "PI EAST PS", "PI BARKE PS"
    );

    private static final Set<String> SOUTH_STATIONS = Set.of(
            "M RURAL PS", "ULLALA PS", "ULLAL PS", "KANKANADY PS", "ACP SOUTH",
            "SOUTH SUB DIV", "PI M RURAL", "PI ULLALA PS", "PI ULLAL PS", "PI KANKANADY PS",
            "PI M. RURAL PS", "PI KTPS"
    );

    private static final Set<String> NORTH_STATIONS = Set.of(
            "PANAMBURU PS", "URWA PS", "MULKI PS", "MOODBIDRE PS", "KAVOOR PS",
            "SURATHKAL PS", "BAJPE PS", "ACP NORTH", "NORTH SUB DIV",
            "PI PANAMBURU PS", "PI PANAMBURUS", "PI PANABURPS", "PI URWA PS", "PI URWA DUTY",
            "PI MULKI PS", "PI MULKY PS", "PI MOODBIDRE PS", "PI MUDUBIDRI",
            "PI KAVOOR PS", "PI KAVOORU PS", "PI SURATHKAL PS", "PI SURTHKALPS",
            "PI BAJPE PS", "PI BAJAPE PS", "MOODBIDRE"
    );

    private static final Set<String> CAR_UNIT_DUTIES = Set.of(
            "VIP ESCORT DUTY", "VIP ESCORT", "VIP PILOT/ESCORT DUTY", "VIP PILOT/ESCORT",
            "VAN DUTY", "TRUCK DUTY", "XENON DUTY", "WATER CANNON", "WATER JET",
            "WATER CANNON DUTY", "WATER JET DUTY", "RIV DUTY", "RIV",
            "STAND BY", "HQ/STAND BY", "MT OFFICE", "MT OFFICE WRITER",
            "LOG SECTION", "LOG SECTION WRITER", "CAR HQ", "CAR HQ DUTY",
            "CAR HQ/VAN DUTY", "CAR HQ/BUS DUTY", "CAR HQ/XENON DUTY",
            "CAR HQ/WATER CANON DUTY", "CAR HQ/WATER CANNON DUTY",
            "ACP CAR", "ACP CAR DUTY", "DCP CAR", "DCP CAR DUTY", "DCR CAR DUTY",
            "RPI I DUTY", "RPI II DUTY", "RPI III DUTY", "RPI MTO DUTY",
            "RPI I DUTY/VIP ESCORT", "RPI II DUTY/VIP ESCORT", "RPI III DUTY/VIP ESCORT",
            "ASC TEAM DUTY"
    );

    /**
     * Categorizes a personnel record into the appropriate MT sub-section
     * based on their duty type title and duty location.
     * The order of checks matters — more specific matches are checked first.
     */
    String categorizePersonnel(Personnel driver) {
        String dutyType = driver.getDutyType() != null ? driver.getDutyType().toUpperCase().trim() : "";
        String dutyLocation = driver.getDutyLocation() != null ? driver.getDutyLocation().toUpperCase().trim() : "";

        // 1. COMPOL DUTY - duty type contains "COMPOL"
        if (dutyType.contains("COMPOL")) {
            return "COMPOL DUTY";
        }

        // 2. DK SP DUTY - duty type contains "DK SP"
        if (dutyType.contains("DK SP")) {
            return "DK SP DUTY";
        }

        // 3. CCRB UNIT - check BEFORE CCB since "CCRB" contains "CCB"
        if (dutyType.contains("CCRB") || dutyLocation.contains("CCRB")) {
            return "CCRB UNIT";
        }

        // 4. CCB UNIT - duty type or location contains "CCB" (but not CCRB, already matched above)
        if (dutyType.contains("CCB") || dutyLocation.contains("CCB")) {
            return "CCB UNIT";
        }

        // 5. FINGER PRINT UNIT
        if (dutyType.contains("FINGER PRINT") || dutyLocation.contains("FINGER PRINT")) {
            return "FINGER PRINT UNIT";
        }

        // 6. CSB UNIT - check BEFORE CEN since both are short
        if (dutyType.contains("CSB") || dutyLocation.contains("CSB")) {
            return "CSB UNIT";
        }

        // 7. CEN UNIT - duty contains "CEN PS" or "PI CEN"
        if (dutyType.contains("CEN PS") || dutyLocation.contains("CEN PS")
                || dutyType.contains("PI CEN") || dutyLocation.contains("PI CEN")) {
            return "CEN UNIT";
        }

        // 8. HIGHWAY PATROL DUTY - matches "HIGHWAY PETROL", "HIGHWAY PATROL", "HIGH PAT"
        if (dutyType.contains("HIGHWAY PETROL") || dutyType.contains("HIGHWAY PATROL")
                || dutyType.contains("HIGH PAT")
                || dutyLocation.contains("HIGHWAY PETROL") || dutyLocation.contains("HIGHWAY PATROL")
                || dutyLocation.contains("HIGH PAT")) {
            return "HIGHWAY PATROL DUTY";
        }

        // 9. HOYSALA DUTY - duty type or location contains "HOYSALA"
        if (dutyType.contains("HOYSALA") || dutyLocation.contains("HOYSALA")) {
            return "HOYSALA DUTY";
        }

        // 10. TRAFFIC SUBDIVISION - duty type or location contains "TRAFFIC"
        if (dutyType.contains("TRAFFIC") || dutyLocation.contains("TRAFFIC")) {
            return "TRAFFIC SUBDIVISION";
        }

        // 11. DCP'S DUTY - duty type contains "DCP" (checked after TRAFFIC since DCP CRIME & TRAFFIC could overlap)
        if (dutyType.contains("DCP") || dutyLocation.contains("DCP")) {
            return "DCP'S DUTY";
        }

        // 12. CENTRAL SUBDIVISION - check by station names in duty location or duty type
        if (matchesStationList(dutyLocation, CENTRAL_STATIONS) || matchesStationList(dutyType, CENTRAL_STATIONS)) {
            return "CENTRAL SUBDIVISION";
        }

        // 13. SOUTH SUBDIVISION
        if (matchesStationList(dutyLocation, SOUTH_STATIONS) || matchesStationList(dutyType, SOUTH_STATIONS)) {
            return "SOUTH SUBDIVISION";
        }

        // 14. NORTH SUBDIVISION
        if (matchesStationList(dutyLocation, NORTH_STATIONS) || matchesStationList(dutyType, NORTH_STATIONS)) {
            return "NORTH SUBDIVISION";
        }

        // 15. CAR UNIT - matches various CAR HQ duties, VIP escort, van, truck, etc.
        if (matchesCarUnit(dutyType, dutyLocation)) {
            return "CAR UNIT";
        }

        return "OTHER";
    }

    private boolean matchesStationList(String value, Set<String> stations) {
        if (value.isEmpty()) return false;
        // Direct match
        if (stations.contains(value)) return true;
        // Check if value contains any station name
        for (String station : stations) {
            if (value.contains(station)) return true;
        }
        return false;
    }

    private boolean matchesCarUnit(String dutyType, String dutyLocation) {
        // Direct match on known CAR unit duty types
        if (CAR_UNIT_DUTIES.contains(dutyType)) return true;

        // Partial match on CAR unit patterns
        if (dutyType.contains("VIP ESCORT") || dutyType.contains("VIP PILOT")) return true;
        if (dutyType.contains("VAN DUTY") || dutyType.contains("TRUCK DUTY")) return true;
        if (dutyType.contains("XENON") || dutyType.contains("WATER CANNON") || dutyType.contains("WATER JET")) return true;
        if (dutyType.contains("RIV DUTY") || dutyType.contains("RIV ")) return true;
        if (dutyType.contains("STAND BY") || dutyType.contains("HQ/STAND")) return true;
        if (dutyType.contains("MT OFFICE") || dutyType.contains("LOG SECTION")) return true;
        if (dutyType.contains("CAR HQ") || dutyType.contains("CAR HQ/")) return true;
        if (dutyType.contains("RPI") && (dutyType.contains("DUTY") || dutyType.contains("ESCORT"))) return true;
        if (dutyType.contains("ASC TEAM")) return true;

        // Check duty location for CAR HQ assignments
        if (dutyLocation.contains("CAR HQ") || dutyLocation.equals("CAR")
                || dutyLocation.contains("MT OFFICE") || dutyLocation.equals("HEAD QUARTER")
                || dutyLocation.equals("HQ")) return true;

        return false;
    }

}
