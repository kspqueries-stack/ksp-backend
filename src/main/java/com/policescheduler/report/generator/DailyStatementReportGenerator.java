package com.policescheduler.report.generator;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.policescheduler.entity.DutyAssignment;
import com.policescheduler.entity.DutyType;
import com.policescheduler.entity.LeaveRequest;
import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.SectionStrength;
import com.policescheduler.report.PdfReportGenerator;
import com.policescheduler.report.PdfStyleHelper;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.report.ReportLocalizationService;
import com.policescheduler.report.ReportRequest;
import com.policescheduler.repository.DutyAssignmentRepository;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.LeaveRequestRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.SectionStrengthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DailyStatementReportGenerator implements PdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(DailyStatementReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Color HEADER_BG = new Color(40, 40, 40);
    private static final Color SECTION_BG = new Color(230, 230, 230);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.BLACK);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 7, Font.BOLD, Color.WHITE);
    private static final Font SECTION_HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);
    private static final Font BOLD_CELL_FONT = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
    private static final Font DATE_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);

    private static final List<String> DESIGNATION_COLS = List.of("DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC");

    // CAR Duty Abstract designation columns (different order from strength table)
    private static final List<String> DUTY_ABSTRACT_COLS = List.of("RPI", "RSI", "ARSI", "AHC", "APC", "SWP");

    private final PersonnelRepository personnelRepository;
    private final DutyAssignmentRepository dutyAssignmentRepository;
    private final DutyTypeRepository dutyTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final SectionStrengthRepository sectionStrengthRepository;
    private final PdfStyleHelper pdfStyleHelper;
    private final ReportLocalizationService localizationService;

    public DailyStatementReportGenerator(PersonnelRepository personnelRepository,
                                          DutyAssignmentRepository dutyAssignmentRepository,
                                          DutyTypeRepository dutyTypeRepository,
                                          LeaveRequestRepository leaveRequestRepository,
                                          SectionStrengthRepository sectionStrengthRepository,
                                          PdfStyleHelper pdfStyleHelper,
                                          ReportLocalizationService localizationService) {
        this.personnelRepository = personnelRepository;
        this.dutyAssignmentRepository = dutyAssignmentRepository;
        this.dutyTypeRepository = dutyTypeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.sectionStrengthRepository = sectionStrengthRepository;
        this.pdfStyleHelper = pdfStyleHelper;
        this.localizationService = localizationService;
    }

    @Override
    public String getReportType() {
        return "form_168";
    }

    @Override
    public byte[] generate(ReportRequest request) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate(), 15, 15, 15, 15);
            PdfWriter.getInstance(document, baos);
            document.open();

            LocalDate reportDate = request.getDate() != null ? request.getDate() : LocalDate.now();

            // Header
            addFormHeader(document, reportDate);

            // Get all active personnel
            List<Personnel> allPersonnel = personnelRepository.findAll().stream()
                    .filter(p -> p.getIsActive() != null && p.getIsActive())
                    .collect(Collectors.toList());

            Map<String, Long> personnelByDesignation = allPersonnel.stream()
                    .filter(p -> p.getDesignation() != null)
                    .collect(Collectors.groupingBy(p -> p.getDesignation().toUpperCase().trim(), Collectors.counting()));

            // === SECTION 1: STRENGTH TABLE ===
            addStrengthTable(document, reportDate, allPersonnel, personnelByDesignation);

            // === SECTION 2: CAR DUTY ABSTRACT ===
            addCarDutyAbstract(document, reportDate, allPersonnel);

            // === SECTION 3: LEAVE/W-OFF/PERMISSION/ABSENT/SICK/SUSPENSION ===
            addLeaveSection(document, reportDate, allPersonnel);

            // === SECTION 4: DETAILED PERSONNEL LISTINGS ===
            document.newPage();
            addDetailedPersonnelSection(document, reportDate, allPersonnel);

            // === SECTION 5: LEAVE STATEMENT ===
            addLeaveStatementSection(document, reportDate, allPersonnel);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate daily statement report", e);
            throw new ReportGenerationException("form_168", "Failed to generate daily statement report", e);
        }
    }

    private void addFormHeader(Document document, LocalDate reportDate) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{50, 50});

        PdfPCell formCell = new PdfPCell(new Phrase("FORM NO:168", TITLE_FONT));
        formCell.setBorder(Rectangle.NO_BORDER);
        formCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        headerTable.addCell(formCell);

        PdfPCell dateCell = new PdfPCell(new Phrase("DATE :" + reportDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), DATE_FONT));
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        headerTable.addCell(dateCell);

        document.add(headerTable);
        document.add(new Paragraph(" ", CELL_FONT));
    }

    private void addStrengthTable(Document document, LocalDate reportDate,
                                   List<Personnel> allPersonnel, Map<String, Long> personnelByDesignation) throws DocumentException {
        Paragraph strengthTitle = new Paragraph("STRENGTH", SECTION_HEADER_FONT);
        strengthTitle.setSpacingAfter(3);
        document.add(strengthTitle);

        // Get strength data
        List<SectionStrength> strengthData = sectionStrengthRepository.findAll().stream()
                .filter(s -> !s.getEffectiveDate().isAfter(reportDate))
                .collect(Collectors.toList());

        Map<String, Integer> sanctioned = new HashMap<>();
        Map<String, Integer> present = new HashMap<>();
        for (SectionStrength ss : strengthData) {
            sanctioned.merge(ss.getDesignation(), ss.getSanctionedCount(), Integer::sum);
            present.merge(ss.getDesignation(), ss.getPresentCount(), Integer::sum);
        }

        PdfPTable table = new PdfPTable(9); // HEADS + 7 designations + TOTAL
        table.setWidthPercentage(100);
        table.setWidths(new float[]{18, 9, 9, 9, 9, 9, 9, 9, 11});

        // Header
        addDarkHeaderCell(table, "HEADS");
        for (String col : DESIGNATION_COLS) addDarkHeaderCell(table, col);
        addDarkHeaderCell(table, "TOTAL");

        // Rows
        String[][] rows = {
                {"C.A.R SANCTIONED STRENGTH"},
                {"PRESENT STRENGTH TOTAL"},
                {"ACTUAL VACANCY"},
                {"PRESENT STRENGTH PMT"},
                {"PRESENT STRENGTH COMPANY"},
                {"RECRUIT APC'S"}
        };

        for (String[] rowDef : rows) {
            String label = rowDef[0];
            addCell(table, label, CELL_FONT, Element.ALIGN_LEFT);
            int rowTotal = 0;
            for (String col : DESIGNATION_COLS) {
                int val = computeStrengthValue(label, col, sanctioned, present, personnelByDesignation);
                rowTotal += val;
                addCell(table, String.valueOf(val), CELL_FONT, Element.ALIGN_CENTER);
            }
            addCell(table, String.valueOf(rowTotal), BOLD_CELL_FONT, Element.ALIGN_CENTER);
        }

        document.add(table);
        document.add(new Paragraph(" ", CELL_FONT));
    }

    private void addCarDutyAbstract(Document document, LocalDate reportDate, List<Personnel> allPersonnel) throws DocumentException {
        Paragraph title = new Paragraph("CAR DUTY ABSTRACT", SECTION_HEADER_FONT);
        title.setSpacingAfter(3);
        document.add(title);

        PdfPTable table = new PdfPTable(8); // HEADS + RPI, RSI, ARSI, AHC, APC, SWP, TOTAL
        table.setWidthPercentage(100);
        table.setWidths(new float[]{30, 8, 8, 8, 10, 10, 8, 10});

        // Header
        addDarkHeaderCell(table, "HEADS");
        for (String col : DUTY_ABSTRACT_COLS) addDarkHeaderCell(table, col);
        addDarkHeaderCell(table, "TOTAL");

        // Get current duty assignments and group by parent category
        List<DutyAssignment> currentAssignments = dutyAssignmentRepository.findByIsCurrentTrue();
        List<DutyType> allDutyTypes = dutyTypeRepository.findAll();
        Map<Long, DutyType> dutyTypeMap = allDutyTypes.stream()
                .collect(Collectors.toMap(DutyType::getId, d -> d));
        Map<Long, Personnel> personnelMap = personnelRepository.findAllById(
                currentAssignments.stream().map(DutyAssignment::getPersonnelId)
                        .distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Personnel::getId, p -> p));

        // Categorize personnel by duty abstract sections
        // Section 1: PMT SECTION
        long pmtTotal = allPersonnel.stream().filter(p -> "PMT".equalsIgnoreCase(p.getSection())).count();
        addBoldRow(table, "1", "PMT SECTION", countByDesignationAbstract(
                allPersonnel.stream().filter(p -> "PMT".equalsIgnoreCase(p.getSection())).collect(Collectors.toList())));

        // Section 2: ESSENTIAL / FIXED DUTY'S
        addSectionHeaderRow(table, "2", "ESSENTIAL / FIXED DUTY'S");
        Map<String, List<Personnel>> essentialDuties = categorizeEssentialDuties(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        int essSlNo = 1;
        int[] essentialTotals = new int[7]; // RPI,RSI,ARSI,AHC,APC,SWP,TOTAL
        for (Map.Entry<String, List<Personnel>> entry : essentialDuties.entrySet()) {
            int[] counts = countByDesignationAbstract(entry.getValue());
            addDataRow(table, essSlNo++, entry.getKey(), counts);
            for (int i = 0; i < 7; i++) essentialTotals[i] += counts[i];
        }
        addTotalRow(table, essentialTotals);

        // Section 3: OOD/TRAINING
        addSectionHeaderRow(table, "3", "OOD/ TRAINING");
        Map<String, List<Personnel>> oodDuties = categorizeOodTraining(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        int oodSlNo = 1;
        int[] oodTotals = new int[7];
        for (Map.Entry<String, List<Personnel>> entry : oodDuties.entrySet()) {
            int[] counts = countByDesignationAbstract(entry.getValue());
            addDataRow(table, oodSlNo++, entry.getKey(), counts);
            for (int i = 0; i < 7; i++) oodTotals[i] += counts[i];
        }
        addTotalRow(table, oodTotals);

        // Section 4: DAILY ROUTINE DUTY'S
        addSectionHeaderRow(table, "4", "DAILY ROUTINE DUTY'S");
        Map<String, List<Personnel>> dailyDuties = categorizeDailyRoutine(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        int dailySlNo = 1;
        int[] dailyTotals = new int[7];
        for (Map.Entry<String, List<Personnel>> entry : dailyDuties.entrySet()) {
            int[] counts = countByDesignationAbstract(entry.getValue());
            addDataRow(table, dailySlNo++, entry.getKey(), counts);
            for (int i = 0; i < 7; i++) dailyTotals[i] += counts[i];
        }
        addTotalRow(table, dailyTotals);

        // Section 5: LEAVE, W/OFF, PERMISSION, ABSENT, SICK, SUSPENSION
        addSectionHeaderRow(table, "5", "LEAVE, W/OFF, PERMISSION, ABSENT, SICK, SUSPENSION");
        Map<String, List<Personnel>> leaveDuties = categorizeLeaveAbstract(allPersonnel, reportDate);
        int leaveSlNo = 1;
        int[] leaveTotals = new int[7];
        for (Map.Entry<String, List<Personnel>> entry : leaveDuties.entrySet()) {
            int[] counts = countByDesignationAbstract(entry.getValue());
            addDataRow(table, leaveSlNo++, entry.getKey(), counts);
            for (int i = 0; i < 7; i++) leaveTotals[i] += counts[i];
        }
        addTotalRow(table, leaveTotals);

        // Grand Total
        int[] grandTotal = new int[7];
        for (int i = 0; i < 7; i++) {
            grandTotal[i] = essentialTotals[i] + oodTotals[i] + dailyTotals[i] + leaveTotals[i];
        }
        addCell(table, "TOTAL", BOLD_CELL_FONT, Element.ALIGN_LEFT);
        for (int i = 0; i < 6; i++) addCell(table, String.valueOf(grandTotal[i]), BOLD_CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(grandTotal[6]), BOLD_CELL_FONT, Element.ALIGN_CENTER);

        document.add(table);
        document.add(new Paragraph(" ", CELL_FONT));
    }

    private void addLeaveSection(Document document, LocalDate reportDate, List<Personnel> allPersonnel) throws DocumentException {
        // This is included in the CAR DUTY ABSTRACT section 5 above
    }

    private void addDetailedPersonnelSection(Document document, LocalDate reportDate, List<Personnel> allPersonnel) throws DocumentException {
        // Detailed page with individual names, badges, designations for each category
        // Matches pages 2-4 of the reference PDF

        List<DutyAssignment> currentAssignments = dutyAssignmentRepository.findByIsCurrentTrue();
        List<DutyType> allDutyTypes = dutyTypeRepository.findAll();
        Map<Long, DutyType> dutyTypeMap = allDutyTypes.stream()
                .collect(Collectors.toMap(DutyType::getId, d -> d));
        Map<Long, Personnel> personnelMap = personnelRepository.findAllById(
                currentAssignments.stream().map(DutyAssignment::getPersonnelId)
                        .distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Personnel::getId, p -> p));

        // Build table: SL.NO, ACP, RPI, RSI, ARSI, AHC, APC, SWP, TOTAL, PERSONNEL DETAILS
        PdfPTable table = new PdfPTable(10);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{28, 5, 5, 5, 5, 7, 7, 5, 7, 26});

        // Header
        addDarkHeaderCell(table, "HEADS");
        addDarkHeaderCell(table, "ACP");
        addDarkHeaderCell(table, "RPI");
        addDarkHeaderCell(table, "RSI");
        addDarkHeaderCell(table, "ARSI");
        addDarkHeaderCell(table, "AHC");
        addDarkHeaderCell(table, "APC");
        addDarkHeaderCell(table, "SWP");
        addDarkHeaderCell(table, "TOTAL");
        addDarkHeaderCell(table, "PERSONNEL DETAILS");

        // GUARD section
        addDetailSectionHeader(table, "GUARD");
        Map<String, List<Personnel>> guards = categorizeGuards(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        int slNo = 1;
        for (Map.Entry<String, List<Personnel>> entry : guards.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // CHAMBER SENTRY/GARDEN DUTY
        addDetailSectionHeader(table, "CHAMBER SENTRY/GARDEN DUTY");
        Map<String, List<Personnel>> chamberDuties = categorizeChamberSentry(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : chamberDuties.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // GUNMAN DETAILS
        addDetailSectionHeader(table, "GUNMAN DETAILS");
        Map<String, List<Personnel>> gunmanDuties = categorizeGunman(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : gunmanDuties.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // VIP ESCORTS/PILOT/PSO
        addDetailSectionHeader(table, "VIP ESCORTS/PILOT/PSO");
        Map<String, List<Personnel>> vipEscorts = categorizeVipEscort(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : vipEscorts.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // STRIKING FORCE / FOLLOW UP PARTY/RIV/B,B
        addDetailSectionHeader(table, "STRIKING FORCE / FOLLOW UP PARTY/RIV/B,B");
        Map<String, List<Personnel>> strikingForce = categorizeStrikingForce(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : strikingForce.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // CHECK POST DUTY
        addDetailSectionHeader(table, "CHECK POST DUTY");
        Map<String, List<Personnel>> checkPost = categorizeCheckPost(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : checkPost.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // PRISONER ESCORT /COURT PROPERTY ESCORT
        addDetailSectionHeader(table, "PRISONER ESCORT /COURT PROPERTY ESCORT");
        Map<String, List<Personnel>> prisoner = categorizePrisonerEscort(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : prisoner.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // PMT
        addDetailSectionHeader(table, "PMT");
        List<Personnel> pmtPersonnel = allPersonnel.stream()
                .filter(p -> "PMT".equalsIgnoreCase(p.getSection()))
                .collect(Collectors.toList());
        addDetailRow(table, slNo++, "P.M.T", pmtPersonnel);

        // DOG SQUAD
        addDetailSectionHeader(table, "DOG SQUAD");
        List<Personnel> dogSquad = allPersonnel.stream()
                .filter(p -> hasDutyContaining(p, "DOG"))
                .collect(Collectors.toList());
        if (!dogSquad.isEmpty()) addDetailRow(table, slNo++, "DOG SQUAD, CAR MANGALORE", dogSquad);

        // HQ
        addDetailSectionHeader(table, "HQ");
        Map<String, List<Personnel>> hqDuties = categorizeHQ(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : hqDuties.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // UNDER TRAINING/WITHOUT TRAINING
        addDetailSectionHeader(table, "UNDER TRAINING/WITHOUT TRAINING");
        Map<String, List<Personnel>> training = categorizeTraining(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : training.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // OOD DUTY
        addDetailSectionHeader(table, "OOD DUTY");
        Map<String, List<Personnel>> oodDuties = categorizeOodDetailed(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : oodDuties.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        // OTHER DUTY
        addDetailSectionHeader(table, "OTHER DUTY");
        Map<String, List<Personnel>> otherDuties = categorizeOtherDuty(allPersonnel, currentAssignments, dutyTypeMap, personnelMap);
        for (Map.Entry<String, List<Personnel>> entry : otherDuties.entrySet()) {
            addDetailRow(table, slNo++, entry.getKey(), entry.getValue());
        }

        document.add(table);
    }

    private void addLeaveStatementSection(Document document, LocalDate reportDate, List<Personnel> allPersonnel) throws DocumentException {
        document.add(new Paragraph(" ", CELL_FONT));
        Paragraph title = new Paragraph("LEAVE STATEMENT", SECTION_HEADER_FONT);
        title.setSpacingAfter(3);
        document.add(title);

        List<LeaveRequest> activeLeaves = leaveRequestRepository.findAll().stream()
                .filter(lr -> "APPROVED".equals(lr.getStatus()))
                .filter(lr -> !lr.getStartDate().isAfter(reportDate) && !lr.getEndDate().isBefore(reportDate))
                .collect(Collectors.toList());

        Map<Long, Personnel> leavePersonnelMap = personnelRepository.findAllById(
                activeLeaves.stream().map(LeaveRequest::getPersonnelId)
                        .distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Personnel::getId, p -> p));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{5, 20, 15, 60});

        addDarkHeaderCell(table, "SL");
        addDarkHeaderCell(table, "BADGE/NAME");
        addDarkHeaderCell(table, "TYPE");
        addDarkHeaderCell(table, "DETAILS");

        // Group by leave type
        Map<String, List<LeaveRequest>> leavesByType = activeLeaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getLeaveType));

        int slNo = 1;
        for (Map.Entry<String, List<LeaveRequest>> entry : leavesByType.entrySet()) {
            // Section header for leave type
            PdfPCell typeHeader = new PdfPCell(new Phrase(entry.getKey(), BOLD_CELL_FONT));
            typeHeader.setColspan(4);
            typeHeader.setBackgroundColor(SECTION_BG);
            typeHeader.setPadding(3);
            table.addCell(typeHeader);

            for (LeaveRequest lr : entry.getValue()) {
                Personnel p = leavePersonnelMap.get(lr.getPersonnelId());
                if (p == null) continue;

                addCell(table, String.valueOf(slNo++), CELL_FONT, Element.ALIGN_CENTER);
                String nameOrBadge = p.getPersonName() != null && !p.getPersonName().isEmpty()
                        ? p.getPersonName() : p.getBadgeId();
                addCell(table, nameOrBadge, CELL_FONT, Element.ALIGN_LEFT);
                addCell(table, lr.getLeaveType(), CELL_FONT, Element.ALIGN_CENTER);

                long days = ChronoUnit.DAYS.between(lr.getStartDate(), lr.getEndDate()) + 1;
                String details = String.format("%d DAYS %s FROM %s TO %s",
                        days, lr.getLeaveType(),
                        lr.getStartDate().format(DATE_FORMAT),
                        lr.getEndDate().format(DATE_FORMAT));
                addCell(table, details, CELL_FONT, Element.ALIGN_LEFT);
            }
        }

        if (activeLeaves.isEmpty()) {
            PdfPCell noData = new PdfPCell(new Phrase("No leave records for this date", CELL_FONT));
            noData.setColspan(4);
            noData.setHorizontalAlignment(Element.ALIGN_CENTER);
            noData.setPadding(5);
            table.addCell(noData);
        }

        document.add(table);
    }

    // === Helper methods ===

    private int[] countByDesignationAbstract(List<Personnel> personnel) {
        // Returns: RPI, RSI, ARSI, AHC, APC, SWP, TOTAL
        int[] counts = new int[7];
        for (Personnel p : personnel) {
            String desig = p.getDesignation() != null ? p.getDesignation().toUpperCase().trim() : "";
            switch (desig) {
                case "RPI": counts[0]++; break;
                case "RSI": counts[1]++; break;
                case "ARSI": counts[2]++; break;
                case "AHC": counts[3]++; break;
                case "APC": counts[4]++; break;
            }
        }
        counts[5] = 0; // SWP always 0
        counts[6] = counts[0] + counts[1] + counts[2] + counts[3] + counts[4];
        return counts;
    }

    private boolean hasDutyContaining(Personnel p, String keyword) {
        String dt = p.getDutyType() != null ? p.getDutyType().toUpperCase() : "";
        String dl = p.getDutyLocation() != null ? p.getDutyLocation().toUpperCase() : "";
        return dt.contains(keyword) || dl.contains(keyword);
    }

    private String formatPersonnelList(List<Personnel> personnel) {
        if (personnel.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> badgesByDesignation = new LinkedHashMap<>();

        for (Personnel p : personnel) {
            String desig = p.getDesignation() != null ? p.getDesignation().toUpperCase().trim() : "OTHER";
            String display;
            if (p.getPersonName() != null && !p.getPersonName().isEmpty()
                    && (desig.equals("RPI") || desig.equals("RSI") || desig.equals("ARSI"))) {
                display = desig + " SRI " + p.getPersonName();
                if (sb.length() > 0) sb.append(", ");
                sb.append(display);
            } else {
                String badgeNum = p.getBadgeId() != null ? p.getBadgeId().replaceAll(".*-", "") : "";
                badgesByDesignation.computeIfAbsent(desig, k -> new ArrayList<>()).add(badgeNum);
            }
        }

        for (Map.Entry<String, List<String>> entry : badgesByDesignation.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.getKey()).append("-").append(String.join(", ", entry.getValue()));
        }

        return sb.toString();
    }

    // === Categorization methods ===

    private Map<String, List<Personnel>> categorizeEssentialDuties(List<Personnel> all,
            List<DutyAssignment> assignments, Map<Long, DutyType> dtMap, Map<Long, Personnel> pMap) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("GUARDS", all.stream().filter(p -> hasDutyContaining(p, "GUARD") && !hasDutyContaining(p, "DOG")).collect(Collectors.toList()));
        result.put("CHAMBER SENTRY/GARDEN DUTY", all.stream().filter(p -> hasDutyContaining(p, "CHAMBER") || hasDutyContaining(p, "GARDEN")).collect(Collectors.toList()));
        result.put("GUNMAN", all.stream().filter(p -> hasDutyContaining(p, "GUNMAN") || hasDutyContaining(p, "GUN MAN")).collect(Collectors.toList()));
        result.put("DOG SQUAD (DOG HANDLERS)", all.stream().filter(p -> hasDutyContaining(p, "DOG")).collect(Collectors.toList()));
        result.put("HQ/ACP/RPI/RSI/DUTY OFFICER/A.D.O/ROUNDS", all.stream().filter(p -> hasDutyContaining(p, "DUTY OFFICER") || hasDutyContaining(p, "ADO") || hasDutyContaining(p, "A.D.O")).collect(Collectors.toList()));
        result.put("WRITERS (DCP, ACP, RPI & DUTY)", all.stream().filter(p -> hasDutyContaining(p, "WRITER")).collect(Collectors.toList()));
        result.put("ASC TEAM", all.stream().filter(p -> hasDutyContaining(p, "ASC TEAM")).collect(Collectors.toList()));
        result.put("ARMOURY", all.stream().filter(p -> hasDutyContaining(p, "ARMOURY") || hasDutyContaining(p, "ARMOURER")).collect(Collectors.toList()));
        result.put("AROGYA BHAGYA CO-ORDINATOR", all.stream().filter(p -> hasDutyContaining(p, "AROGYA")).collect(Collectors.toList()));
        result.put("POLICE CANTEEN DUTY", all.stream().filter(p -> hasDutyContaining(p, "CANTEEN")).collect(Collectors.toList()));
        result.put("CAR STORE I/C", all.stream().filter(p -> hasDutyContaining(p, "CAR STORE")).collect(Collectors.toList()));
        result.put("BAJPE AIRPORT LIASON DUTY", all.stream().filter(p -> hasDutyContaining(p, "BAJPE") && hasDutyContaining(p, "LAISON")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeOodTraining(List<Personnel> all,
            List<DutyAssignment> assignments, Map<Long, DutyType> dtMap, Map<Long, Personnel> pMap) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("OOD TO BDDS WESTERN RANGE MANGALURU", all.stream().filter(p -> hasDutyContaining(p, "BDDS")).collect(Collectors.toList()));
        result.put("OOD TO CCT KOODLU BENGALURU", all.stream().filter(p -> hasDutyContaining(p, "CCT") && hasDutyContaining(p, "KOODLU")).collect(Collectors.toList()));
        result.put("OOD TO CONTROL ROOM MANGALURU", all.stream().filter(p -> hasDutyContaining(p, "CONTROL ROOM")).collect(Collectors.toList()));
        result.put("OOD TO FPB MANGALURU", all.stream().filter(p -> hasDutyContaining(p, "FPB")).collect(Collectors.toList()));
        result.put("OOD TO DCP CRIME & TRAFFIC OFFICE", all.stream().filter(p -> hasDutyContaining(p, "OOD") && hasDutyContaining(p, "DCP CRIME")).collect(Collectors.toList()));
        result.put("BASIC TRAINING PTS MYSURU", all.stream().filter(p -> hasDutyContaining(p, "PTS MYSURU") || hasDutyContaining(p, "TRAINING") && hasDutyContaining(p, "MYSURU")).collect(Collectors.toList()));
        result.put("BASIC TRAINING PTS DHARWAD", all.stream().filter(p -> hasDutyContaining(p, "PTS DHARWAD") || hasDutyContaining(p, "TRAINING") && hasDutyContaining(p, "DHARWAD")).collect(Collectors.toList()));
        result.put("PDMS TRAINING", all.stream().filter(p -> hasDutyContaining(p, "PDMS") && hasDutyContaining(p, "TRAINING")).collect(Collectors.toList()));
        result.put("CCT TRG KOODLU", all.stream().filter(p -> hasDutyContaining(p, "CCT") && hasDutyContaining(p, "TRG")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeDailyRoutine(List<Personnel> all,
            List<DutyAssignment> assignments, Map<Long, DutyType> dtMap, Map<Long, Personnel> pMap) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("VIP ESCORT & PILOT / VIP CONVOY", all.stream().filter(p -> hasDutyContaining(p, "VIP ESCORT") || hasDutyContaining(p, "VIP PILOT")).collect(Collectors.toList()));
        result.put("STRIKING FORCE / FOLLOW UP PARTY/RIV/B,B/ CHECK POINT DUTY", all.stream().filter(p -> hasDutyContaining(p, "STRIKING") || hasDutyContaining(p, "RIV") || hasDutyContaining(p, "CHECK POINT") || hasDutyContaining(p, "FOLLOW UP")).collect(Collectors.toList()));
        result.put("PRISONER ESCORT/COURT PROPERTY", all.stream().filter(p -> hasDutyContaining(p, "PRISONER") || hasDutyContaining(p, "COURT")).collect(Collectors.toList()));
        result.put("PHOTOGRAPHER", all.stream().filter(p -> hasDutyContaining(p, "PHOTOGRAPHER")).collect(Collectors.toList()));
        result.put("VIP SPARE/ CAR HQ", all.stream().filter(p -> hasDutyContaining(p, "VIP SPARE") || hasDutyContaining(p, "CAR HQ") && !hasDutyContaining(p, "VIP ESCORT")).collect(Collectors.toList()));
        result.put("DUTY TO COP COMPUTER WING", all.stream().filter(p -> hasDutyContaining(p, "COP COMPUTER")).collect(Collectors.toList()));
        result.put("KABBADDI SELECTION TO DAVANAGERE", all.stream().filter(p -> hasDutyContaining(p, "KABBADDI")).collect(Collectors.toList()));
        result.put("CP ESTATE IC", all.stream().filter(p -> hasDutyContaining(p, "CP ESTATE")).collect(Collectors.toList()));
        result.put("CHECK POST CHECKING DUTY", all.stream().filter(p -> hasDutyContaining(p, "CHECK POST CHECKING")).collect(Collectors.toList()));
        result.put("SPL DUTY REPORT TO CEN PS", all.stream().filter(p -> hasDutyContaining(p, "SPL DUTY") || hasDutyContaining(p, "CEN PS")).collect(Collectors.toList()));
        result.put("CASH ESCORT", all.stream().filter(p -> hasDutyContaining(p, "CASH ESCORT")).collect(Collectors.toList()));
        result.put("BAJPE AIRPORT OP DUTY", all.stream().filter(p -> hasDutyContaining(p, "BAJPE") && hasDutyContaining(p, "OP")).collect(Collectors.toList()));
        result.put("POLICE LANE BEAT DUTY", all.stream().filter(p -> hasDutyContaining(p, "POLICE LANE") || hasDutyContaining(p, "BEAT DUTY")).collect(Collectors.toList()));
        result.put("TAPPAL DUTY TO BENGALURU", all.stream().filter(p -> hasDutyContaining(p, "TAPPAL")).collect(Collectors.toList()));
        result.put("GUARD CHECK", all.stream().filter(p -> hasDutyContaining(p, "GUARD CHECK")).collect(Collectors.toList()));
        result.put("COURT SUPERVISION", all.stream().filter(p -> hasDutyContaining(p, "COURT SUPERVISION")).collect(Collectors.toList()));
        result.put("LAST NIGHT ADO", all.stream().filter(p -> hasDutyContaining(p, "LAST NIGHT ADO")).collect(Collectors.toList()));
        result.put("CAR BUILDING MAINTENANCE", all.stream().filter(p -> hasDutyContaining(p, "CAR BUILDING")).collect(Collectors.toList()));
        result.put("DIST CONSUMER DISPUTES OFFICE DUTY", all.stream().filter(p -> hasDutyContaining(p, "CONSUMER")).collect(Collectors.toList()));
        result.put("RECRUIT APC'S", all.stream().filter(p -> hasDutyContaining(p, "RECRUIT")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeLeaveAbstract(List<Personnel> all, LocalDate reportDate) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findAll().stream()
                .filter(lr -> "APPROVED".equals(lr.getStatus()))
                .filter(lr -> !lr.getStartDate().isAfter(reportDate) && !lr.getEndDate().isBefore(reportDate))
                .collect(Collectors.toList());
        Set<Long> onLeaveIds = activeLeaves.stream().map(LeaveRequest::getPersonnelId).collect(Collectors.toSet());
        Map<String, Set<Long>> byType = activeLeaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getLeaveType, Collectors.mapping(LeaveRequest::getPersonnelId, Collectors.toSet())));

        result.put("LEAVE (CL/CML/EL/PL)", all.stream().filter(p -> onLeaveIds.contains(p.getId())).collect(Collectors.toList()));
        result.put("WEEKLY OFF/PERMISSION", new ArrayList<>()); // placeholder
        result.put("ABSENT", new ArrayList<>());
        result.put("SUSPENSION", new ArrayList<>());
        result.put("SICK", all.stream().filter(p -> byType.getOrDefault("SICK", Set.of()).contains(p.getId())).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    // Detailed section categorization methods
    private Map<String, List<Personnel>> categorizeGuards(List<Personnel> all, List<DutyAssignment> assignments, Map<Long, DutyType> dtMap, Map<Long, Personnel> pMap) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("COMMISSIONER OFFICE GUARD (COP)", all.stream().filter(p -> hasDutyContaining(p, "COMMISSIONER") && hasDutyContaining(p, "GUARD")).collect(Collectors.toList()));
        result.put("DISTRICT TREASURY GUARD", all.stream().filter(p -> hasDutyContaining(p, "TREASURY") && hasDutyContaining(p, "GUARD")).collect(Collectors.toList()));
        result.put("DIST SESSION JUDGE BUNGLOW GUARD", all.stream().filter(p -> hasDutyContaining(p, "SESSION JUDGE")).collect(Collectors.toList()));
        result.put("JUSTICE RESIDENTIAL GUARD (HAT HILL)", all.stream().filter(p -> hasDutyContaining(p, "JUSTICE") || hasDutyContaining(p, "HAT HILL")).collect(Collectors.toList()));
        result.put("POLICE COMMISSIONER BUNGLOW", all.stream().filter(p -> hasDutyContaining(p, "COMMISSIONER BUNGLOW")).collect(Collectors.toList()));
        result.put("DC BUNGLOW", all.stream().filter(p -> hasDutyContaining(p, "DC BUNGLOW")).collect(Collectors.toList()));
        result.put("FSL GUARD MANGALURU", all.stream().filter(p -> hasDutyContaining(p, "FSL")).collect(Collectors.toList()));
        result.put("WIRELESS MONITORING STATION GUARD", all.stream().filter(p -> hasDutyContaining(p, "WIRELESS")).collect(Collectors.toList()));
        result.put("WENLOCK HOSPITAL CELL GUARD", all.stream().filter(p -> hasDutyContaining(p, "WENLOCK")).collect(Collectors.toList()));
        result.put("NCC PANDESHWARA", all.stream().filter(p -> hasDutyContaining(p, "NCC") && hasDutyContaining(p, "PANDESHWARA")).collect(Collectors.toList()));
        result.put("NCC ARAVINDA (SHIVABHAGH)", all.stream().filter(p -> hasDutyContaining(p, "NCC") && hasDutyContaining(p, "ARAVINDA")).collect(Collectors.toList()));
        result.put("NCC YEKKURU", all.stream().filter(p -> hasDutyContaining(p, "NCC") && hasDutyContaining(p, "YEKKURU")).collect(Collectors.toList()));
        result.put("CORPORATION/UNION BANK CURRENCY CHEST GUARD", all.stream().filter(p -> hasDutyContaining(p, "CORPORATION") && hasDutyContaining(p, "CURRENCY")).collect(Collectors.toList()));
        result.put("SYNDICATE/CANARA BANK CURRENCY CHEST GUARD", all.stream().filter(p -> hasDutyContaining(p, "SYNDICATE") && hasDutyContaining(p, "CURRENCY")).collect(Collectors.toList()));
        result.put("VIJAYA BANK / BANK OF BARODA CURRENCY CHEST GUARD", all.stream().filter(p -> hasDutyContaining(p, "VIJAYA") && hasDutyContaining(p, "CURRENCY")).collect(Collectors.toList()));
        result.put("CANARA BANK CURRENCY CHEST GUARD", all.stream().filter(p -> hasDutyContaining(p, "CANARA") && hasDutyContaining(p, "CURRENCY") && !hasDutyContaining(p, "SYNDICATE")).collect(Collectors.toList()));
        result.put("KARNATAKA BANK CURRENCY CHEST GUARD", all.stream().filter(p -> hasDutyContaining(p, "KARNATAKA") && hasDutyContaining(p, "CURRENCY")).collect(Collectors.toList()));
        result.put("AXIS BANK CURRENCY CHEST GUARD", all.stream().filter(p -> hasDutyContaining(p, "AXIS") && hasDutyContaining(p, "CURRENCY")).collect(Collectors.toList()));
        result.put("ICICI BANK CURRENCY CHEST GUARD", all.stream().filter(p -> hasDutyContaining(p, "ICICI") && hasDutyContaining(p, "CURRENCY")).collect(Collectors.toList()));
        result.put("VVPAT AND EVM GUARD", all.stream().filter(p -> hasDutyContaining(p, "VVPAT") || hasDutyContaining(p, "EVM")).collect(Collectors.toList()));
        result.put("CAR ARMOURY GUARD", all.stream().filter(p -> hasDutyContaining(p, "ARMOURY") && hasDutyContaining(p, "GUARD")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeChamberSentry(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("CP BUNGLOW GARDEN DUTY", all.stream().filter(pp -> hasDutyContaining(pp, "GARDEN")).collect(Collectors.toList()));
        result.put("COMMISSIONER OFFICE CHAMBER SENTRY", all.stream().filter(pp -> hasDutyContaining(pp, "COMMISSIONER") && hasDutyContaining(pp, "CHAMBER")).collect(Collectors.toList()));
        result.put("DCP CAR OFFICE CHAMBER SENTRY", all.stream().filter(pp -> hasDutyContaining(pp, "DCP CAR") && hasDutyContaining(pp, "CHAMBER")).collect(Collectors.toList()));
        result.put("DCP LAW & ORDER OFFICE CHAMBER SENTRY", all.stream().filter(pp -> hasDutyContaining(pp, "DCP LAW") && hasDutyContaining(pp, "CHAMBER")).collect(Collectors.toList()));
        result.put("ACP CAR OFFICE CHAMBER SENTRY", all.stream().filter(pp -> hasDutyContaining(pp, "ACP") && hasDutyContaining(pp, "CHAMBER")).collect(Collectors.toList()));
        result.put("DCP CRIME & TRAFFIC OFFICE CHAMBER SENTRY", all.stream().filter(pp -> hasDutyContaining(pp, "CRIME") && hasDutyContaining(pp, "CHAMBER")).collect(Collectors.toList()));
        result.put("COP GARDENER", all.stream().filter(pp -> hasDutyContaining(pp, "GARDENER")).collect(Collectors.toList()));
        result.put("POLICE LANE IN-CHARGE", all.stream().filter(pp -> hasDutyContaining(pp, "POLICE LANE") && hasDutyContaining(pp, "IN-CHARGE")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeGunman(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("GUNMAN", all.stream().filter(pp -> hasDutyContaining(pp, "GUNMAN") || hasDutyContaining(pp, "GUN MAN")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeVipEscort(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("VIP ESCORTS/PILOT/PSO", all.stream().filter(pp -> hasDutyContaining(pp, "VIP ESCORT") || hasDutyContaining(pp, "VIP PILOT") || hasDutyContaining(pp, "PSO")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeStrikingForce(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("C C ROOM SF-I", all.stream().filter(pp -> hasDutyContaining(pp, "C C ROOM") && hasDutyContaining(pp, "SF-I")).collect(Collectors.toList()));
        result.put("C C ROOM SF-II", all.stream().filter(pp -> hasDutyContaining(pp, "C C ROOM") && hasDutyContaining(pp, "SF-II")).collect(Collectors.toList()));
        result.put("CAR STAND BY -I", all.stream().filter(pp -> hasDutyContaining(pp, "STAND BY") && hasDutyContaining(pp, "-I") && !hasDutyContaining(pp, "-II") && !hasDutyContaining(pp, "-III")).collect(Collectors.toList()));
        result.put("CAR STAND BY -II", all.stream().filter(pp -> hasDutyContaining(pp, "STAND BY") && hasDutyContaining(pp, "-II")).collect(Collectors.toList()));
        result.put("CAR STAND BY -III", all.stream().filter(pp -> hasDutyContaining(pp, "STAND BY") && hasDutyContaining(pp, "-III")).collect(Collectors.toList()));
        result.put("CPT TEAM", all.stream().filter(pp -> hasDutyContaining(pp, "CPT TEAM")).collect(Collectors.toList()));
        result.put("QRT TEAM", all.stream().filter(pp -> hasDutyContaining(pp, "QRT TEAM")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeCheckPost(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("CHECK POST DUTY", all.stream().filter(pp -> hasDutyContaining(pp, "CHECK POST")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizePrisonerEscort(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("PRISONER ESCORT", all.stream().filter(pp -> hasDutyContaining(pp, "PRISONER")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeHQ(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("CAR HQ", all.stream().filter(pp -> hasDutyContaining(pp, "CAR HQ") && !hasDutyContaining(pp, "VIP") && !hasDutyContaining(pp, "VAN") && !hasDutyContaining(pp, "BUS")).collect(Collectors.toList()));
        result.put("DUTY OFFICER /DUTY WRITERS", all.stream().filter(pp -> hasDutyContaining(pp, "DUTY OFFICER")).collect(Collectors.toList()));
        result.put("DCP CAR WRITER AND COMPUTER OPERATOR", all.stream().filter(pp -> hasDutyContaining(pp, "DCP") && hasDutyContaining(pp, "WRITER")).collect(Collectors.toList()));
        result.put("ACP WRITER/ OFFICE", all.stream().filter(pp -> hasDutyContaining(pp, "ACP") && hasDutyContaining(pp, "WRITER")).collect(Collectors.toList()));
        result.put("RPI WRITER", all.stream().filter(pp -> hasDutyContaining(pp, "RPI") && hasDutyContaining(pp, "WRITER")).collect(Collectors.toList()));
        result.put("COMPUTER OPERATOR/E-OFFICE", all.stream().filter(pp -> hasDutyContaining(pp, "COMPUTER") && !hasDutyContaining(pp, "COP")).collect(Collectors.toList()));
        result.put("CAR STORE I/C", all.stream().filter(pp -> hasDutyContaining(pp, "CAR STORE")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeTraining(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("BASIC TRAINING PTS MYSURU", all.stream().filter(pp -> hasDutyContaining(pp, "PTS MYSURU")).collect(Collectors.toList()));
        result.put("BASIC TRAINING PTS DHARWAD", all.stream().filter(pp -> hasDutyContaining(pp, "PTS DHARWAD")).collect(Collectors.toList()));
        result.put("PDMS TRAINING", all.stream().filter(pp -> hasDutyContaining(pp, "PDMS") && hasDutyContaining(pp, "TRAIN")).collect(Collectors.toList()));
        result.put("CCT KOODLU (TRAINING)", all.stream().filter(pp -> hasDutyContaining(pp, "CCT") && hasDutyContaining(pp, "KOODLU")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeOodDetailed(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("OOD TO BDDS WESTERN RANGE MANGALURU", all.stream().filter(pp -> hasDutyContaining(pp, "BDDS")).collect(Collectors.toList()));
        result.put("OOD TO CCT KUDLU", all.stream().filter(pp -> hasDutyContaining(pp, "CCT") && (hasDutyContaining(pp, "KUDLU") || hasDutyContaining(pp, "KOODLU"))).collect(Collectors.toList()));
        result.put("OOD TO FPB MANGALURU", all.stream().filter(pp -> hasDutyContaining(pp, "FPB")).collect(Collectors.toList()));
        result.put("OOD TO CONTROL ROOM MGC", all.stream().filter(pp -> hasDutyContaining(pp, "CONTROL ROOM")).collect(Collectors.toList()));
        result.put("OOD TO DCP CRIME & TRAFFIC OFFICE", all.stream().filter(pp -> hasDutyContaining(pp, "OOD") && hasDutyContaining(pp, "DCP CRIME")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    private Map<String, List<Personnel>> categorizeOtherDuty(List<Personnel> all, List<DutyAssignment> a, Map<Long, DutyType> d, Map<Long, Personnel> p) {
        Map<String, List<Personnel>> result = new LinkedHashMap<>();
        result.put("BAJPE AIRPORT LAISON DUTY", all.stream().filter(pp -> hasDutyContaining(pp, "BAJPE") && hasDutyContaining(pp, "LAISON")).collect(Collectors.toList()));
        result.put("CAR BUILDING MAINTENANCE", all.stream().filter(pp -> hasDutyContaining(pp, "CAR BUILDING")).collect(Collectors.toList()));
        result.put("PHOTOGRAPHER", all.stream().filter(pp -> hasDutyContaining(pp, "PHOTOGRAPHER")).collect(Collectors.toList()));
        result.put("DIST CONSUMER DISPUTES OFFICE DUTY", all.stream().filter(pp -> hasDutyContaining(pp, "CONSUMER")).collect(Collectors.toList()));
        result.put("DUTY TO COP COMPUTER WING", all.stream().filter(pp -> hasDutyContaining(pp, "COP COMPUTER")).collect(Collectors.toList()));
        result.put("KABBADDI SELECTION TO DAVANAGERE", all.stream().filter(pp -> hasDutyContaining(pp, "KABBADDI")).collect(Collectors.toList()));
        result.put("BAJPE AIRPORT OP DUTY", all.stream().filter(pp -> hasDutyContaining(pp, "BAJPE") && hasDutyContaining(pp, "OP")).collect(Collectors.toList()));
        result.put("POLICE LANE BEAT DUTY", all.stream().filter(pp -> hasDutyContaining(pp, "BEAT DUTY")).collect(Collectors.toList()));
        result.put("CP ESTATE IC", all.stream().filter(pp -> hasDutyContaining(pp, "CP ESTATE")).collect(Collectors.toList()));
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    // === Table helper methods ===

    private void addDarkHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(3);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(2);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addBoldRow(PdfPTable table, String slNo, String label, int[] counts) {
        addCell(table, slNo + "  " + label, BOLD_CELL_FONT, Element.ALIGN_LEFT);
        for (int i = 0; i < 6; i++) addCell(table, String.valueOf(counts[i]), BOLD_CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(counts[6]), BOLD_CELL_FONT, Element.ALIGN_CENTER);
    }

    private void addSectionHeaderRow(PdfPTable table, String slNo, String label) {
        PdfPCell cell = new PdfPCell(new Phrase(slNo + "  " + label, BOLD_CELL_FONT));
        cell.setColspan(8);
        cell.setBackgroundColor(SECTION_BG);
        cell.setPadding(3);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addDataRow(PdfPTable table, int slNo, String label, int[] counts) {
        addCell(table, slNo + "  " + label, CELL_FONT, Element.ALIGN_LEFT);
        for (int i = 0; i < 6; i++) addCell(table, String.valueOf(counts[i]), CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(counts[6]), CELL_FONT, Element.ALIGN_CENTER);
    }

    private void addTotalRow(PdfPTable table, int[] totals) {
        addCell(table, "TOTAL", BOLD_CELL_FONT, Element.ALIGN_RIGHT);
        for (int i = 0; i < 6; i++) addCell(table, String.valueOf(totals[i]), BOLD_CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(totals[6]), BOLD_CELL_FONT, Element.ALIGN_CENTER);
    }

    private void addDetailSectionHeader(PdfPTable table, String label) {
        PdfPCell cell = new PdfPCell(new Phrase(label, BOLD_CELL_FONT));
        cell.setColspan(10);
        cell.setBackgroundColor(SECTION_BG);
        cell.setPadding(3);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addDetailRow(PdfPTable table, int slNo, String label, List<Personnel> personnel) {
        // Count by designation: ACP, RPI, RSI, ARSI, AHC, APC, SWP
        int acp = 0, rpi = 0, rsi = 0, arsi = 0, ahc = 0, apc = 0;
        for (Personnel p : personnel) {
            String d = p.getDesignation() != null ? p.getDesignation().toUpperCase().trim() : "";
            switch (d) {
                case "ACP": acp++; break;
                case "RPI": rpi++; break;
                case "RSI": rsi++; break;
                case "ARSI": arsi++; break;
                case "AHC": ahc++; break;
                case "APC": apc++; break;
            }
        }
        int total = acp + rpi + rsi + arsi + ahc + apc;

        addCell(table, label, CELL_FONT, Element.ALIGN_LEFT);
        addCell(table, String.valueOf(acp), CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(rpi), CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(rsi), CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(arsi), CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(ahc), CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, String.valueOf(apc), CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, "0", CELL_FONT, Element.ALIGN_CENTER); // SWP
        addCell(table, String.valueOf(total), CELL_FONT, Element.ALIGN_CENTER);
        addCell(table, formatPersonnelList(personnel), CELL_FONT, Element.ALIGN_LEFT);
    }

    private int computeStrengthValue(String rowLabel, String designation,
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
}
