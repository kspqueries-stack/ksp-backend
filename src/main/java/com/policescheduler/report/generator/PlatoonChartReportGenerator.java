package com.policescheduler.report.generator;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.Platoon;
import com.policescheduler.entity.PlatoonRotationState;
import com.policescheduler.report.PdfReportGenerator;
import com.policescheduler.report.PdfStyleHelper;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.report.ReportLocalizationService;
import com.policescheduler.report.ReportRequest;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.PlatoonRepository;
import com.policescheduler.repository.PlatoonRotationStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlatoonChartReportGenerator implements PdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(PlatoonChartReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    static final List<String> ALL_SECTIONS = List.of("C", "D", "E", "F", "G");

    static final List<String> DUTY_COLUMNS = List.of(
            "GUARD-I", "GUARD-II", "CHECK POINT", "PRISON/VIP ESCORT/OUT", "STRIKING FORCE/HELP"
    );

    private static final Color HEADER_BG = new Color(211, 211, 211);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);
    private static final Font PLATOON_FONT = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.BLACK);

    private static final int NUM_ROTATION_PERIODS = 5;
    private static final int ROTATION_DAYS = 15;

    private final PersonnelRepository personnelRepository;
    private final PlatoonRepository platoonRepository;
    private final PlatoonRotationStateRepository rotationStateRepository;
    private final DutyTypeRepository dutyTypeRepository;
    private final PdfStyleHelper pdfStyleHelper;
    private final ReportLocalizationService localizationService;

    public PlatoonChartReportGenerator(PersonnelRepository personnelRepository,
                                        PlatoonRepository platoonRepository,
                                        PlatoonRotationStateRepository rotationStateRepository,
                                        DutyTypeRepository dutyTypeRepository,
                                        PdfStyleHelper pdfStyleHelper,
                                        ReportLocalizationService localizationService) {
        this.personnelRepository = personnelRepository;
        this.platoonRepository = platoonRepository;
        this.rotationStateRepository = rotationStateRepository;
        this.dutyTypeRepository = dutyTypeRepository;
        this.pdfStyleHelper = pdfStyleHelper;
        this.localizationService = localizationService;
    }

    @Override
    public String getReportType() {
        return "platoon_chart";
    }

    @Override
    public byte[] generate(ReportRequest request) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate(), 5, 5, 5, 5);
            PdfWriter.getInstance(document, baos);
            document.open();

            String locale = request.getLocale() != null ? request.getLocale() : "en";

            // Get rotation state
            PlatoonRotationState state = rotationStateRepository.findAll().stream()
                    .findFirst().orElse(null);

            // Get platoons ordered by base offset
            List<Platoon> platoons = platoonRepository.findAllByOrderByBaseOffsetAsc();

            // Query all active personnel with platoon assignments in sections C-G
            List<Personnel> allPersonnel = personnelRepository.findAll().stream()
                    .filter(p -> p.getIsActive() != null && p.getIsActive())
                    .filter(p -> p.getPlatoonId() != null)
                    .filter(p -> ALL_SECTIONS.contains(p.getSection()))
                    .collect(Collectors.toList());

            // Generate the combined Section C grid (all sections combined into one view)
            generateCombinedGrid(document, locale, allPersonnel, platoons, state);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate platoon chart report", e);
            throw new ReportGenerationException("platoon_chart", "Failed to generate platoon chart report", e);
        }
    }

    private void generateCombinedGrid(Document document, String locale,
                                       List<Personnel> allPersonnel,
                                       List<Platoon> platoons,
                                       PlatoonRotationState state) throws DocumentException {

        // Compute strength per platoon (all personnel across C-G sections)
        Map<Long, long[]> platoonStrength = new HashMap<>();
        for (Platoon p : platoons) {
            long ahc = allPersonnel.stream()
                    .filter(per -> p.getId().equals(per.getPlatoonId()) && "AHC".equals(per.getDesignation()))
                    .count();
            long apc = allPersonnel.stream()
                    .filter(per -> p.getId().equals(per.getPlatoonId()) && "APC".equals(per.getDesignation()))
                    .count();
            platoonStrength.put(p.getId(), new long[]{ahc, apc, ahc + apc});
        }

        int currentCycle = state != null ? state.getCurrentCycleIndex() : 0;

        // 11 columns: DATE + (platoon_label + badge_ids) * 5
        float[] widths = {8f, 4.5f, 13.5f, 4.5f, 13.5f, 4.5f, 13.5f, 4.5f, 13.5f, 4.5f, 13.5f};
        PdfPTable table = new PdfPTable(11);
        table.setWidthPercentage(100);
        table.setWidths(widths);

        // Title row spanning all 11 columns
        PdfPCell titleCell = new PdfPCell(new Phrase("SECTION C", TITLE_FONT));
        titleCell.setColspan(11);
        titleCell.setBackgroundColor(HEADER_BG);
        titleCell.setPadding(4);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(titleCell);

        // Header row 1: DATE + duty column TITLES (each spanning 2 sub-columns)
        PdfPCell dateHeader = new PdfPCell(new Phrase("DATE", HEADER_FONT));
        dateHeader.setBackgroundColor(HEADER_BG);
        dateHeader.setPadding(2);
        dateHeader.setRowspan(2);
        dateHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        dateHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(dateHeader);

        for (int dutyIdx = 0; dutyIdx < DUTY_COLUMNS.size(); dutyIdx++) {
            PdfPCell cell = new PdfPCell(new Phrase(DUTY_COLUMNS.get(dutyIdx), HEADER_FONT));
            cell.setColspan(2);
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(2);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }

        // Header row 2: strength counts per duty column
        for (int dutyIdx = 0; dutyIdx < DUTY_COLUMNS.size(); dutyIdx++) {
            Long platoonAtDuty = null;
            for (Platoon p : platoons) {
                int idx = (p.getBaseOffset() + currentCycle) % 5;
                if (idx == dutyIdx) {
                    platoonAtDuty = p.getId();
                    break;
                }
            }

            long ahc = 0, apc = 0, total = 0;
            if (platoonAtDuty != null && platoonStrength.containsKey(platoonAtDuty)) {
                long[] s = platoonStrength.get(platoonAtDuty);
                ahc = s[0]; apc = s[1]; total = s[2];
            }

            String countText = String.format("%02d AHC, %d APC, TOTAL=%d", ahc, apc, total);
            PdfPCell cell = new PdfPCell(new Phrase(countText, HEADER_FONT));
            cell.setColspan(2);
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(2);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }

        // Data rows: one per rotation period
        LocalDate anchorStart = state != null && state.getLastRotationDate() != null
                ? state.getLastRotationDate() : LocalDate.now();

        for (int period = 0; period < NUM_ROTATION_PERIODS; period++) {
            int cycleForPeriod = currentCycle + period;

            // Compute date range for this period
            LocalDate periodStart = anchorStart.plusDays((long) period * ROTATION_DAYS);
            LocalDate periodEnd = periodStart.plusDays(ROTATION_DAYS - 1);
            String dateRange = periodStart.format(DATE_FORMAT) + "\nTO\n" + periodEnd.format(DATE_FORMAT);

            // Date cell
            PdfPCell dateCell = new PdfPCell(new Phrase(dateRange, CELL_FONT));
            dateCell.setPadding(2);
            dateCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            dateCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            dateCell.setFixedHeight(98f);
            table.addCell(dateCell);

            // For each duty column: platoon label cell (rotated) + badge IDs cell
            for (int dutyIdx = 0; dutyIdx < DUTY_COLUMNS.size(); dutyIdx++) {
                String platoonLabel = "";
                String badgeIds = "";

                for (Platoon p : platoons) {
                    int idx = (p.getBaseOffset() + cycleForPeriod) % 5;
                    if (idx == dutyIdx) {
                        platoonLabel = "PLATOON-" + p.getName().replace("Platoon ", "");

                        // Get personnel in this platoon
                        List<Personnel> platoonPersonnel = allPersonnel.stream()
                                .filter(per -> p.getId().equals(per.getPlatoonId()))
                                .collect(Collectors.toList());

                        if (!platoonPersonnel.isEmpty()) {
                            badgeIds = formatBadgeIds(platoonPersonnel);
                        }
                        break;
                    }
                }

                // Platoon label cell
                PdfPCell platoonCell = new PdfPCell(new Phrase(platoonLabel, PLATOON_FONT));
                platoonCell.setPadding(1);
                platoonCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                platoonCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                platoonCell.setNoWrap(true);
                table.addCell(platoonCell);

                // Badge IDs cell
                PdfPCell badgeCell = new PdfPCell(new Phrase(badgeIds, CELL_FONT));
                badgeCell.setPadding(2);
                badgeCell.setVerticalAlignment(Element.ALIGN_TOP);
                table.addCell(badgeCell);
            }
        }

        document.add(table);
    }

    String formatBadgeIds(List<Personnel> personnel) {
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

    String extractBadgeNumber(String badgeId) {
        if (badgeId != null && badgeId.contains("-")) {
            return badgeId.substring(badgeId.lastIndexOf('-') + 1);
        }
        return badgeId != null ? badgeId : "";
    }
}
