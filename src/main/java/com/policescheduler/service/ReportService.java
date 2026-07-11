package com.policescheduler.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.policescheduler.dto.DutyAssignmentDto;
import com.policescheduler.dto.LeaveFilter;
import com.policescheduler.dto.PersonnelFilter;
import com.policescheduler.entity.*;
import com.policescheduler.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);

    private final PersonnelRepository personnelRepository;
    private final DutyAssignmentRepository dutyAssignmentRepository;
    private final DutyTypeRepository dutyTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final SectionStrengthRepository sectionStrengthRepository;
    private final PlatoonRepository platoonRepository;
    private final PlatoonRotationStateRepository rotationStateRepository;

    public ReportService(PersonnelRepository personnelRepository,
                         DutyAssignmentRepository dutyAssignmentRepository,
                         DutyTypeRepository dutyTypeRepository,
                         LeaveRequestRepository leaveRequestRepository,
                         SectionStrengthRepository sectionStrengthRepository,
                         PlatoonRepository platoonRepository,
                         PlatoonRotationStateRepository rotationStateRepository) {
        this.personnelRepository = personnelRepository;
        this.dutyAssignmentRepository = dutyAssignmentRepository;
        this.dutyTypeRepository = dutyTypeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.sectionStrengthRepository = sectionStrengthRepository;
        this.platoonRepository = platoonRepository;
        this.rotationStateRepository = rotationStateRepository;
    }

    public byte[] generateForm168(LocalDate date) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            Paragraph title = new Paragraph("FORM 168 - DAILY STATEMENT", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph dateP = new Paragraph("Date: " + (date != null ? date : LocalDate.now()).format(DateTimeFormatter.ofPattern("dd-MM-yyyy")), CELL_FONT);
            dateP.setAlignment(Element.ALIGN_CENTER);
            document.add(dateP);
            document.add(new Paragraph(" "));

            // Section Strength
            document.add(new Paragraph("STRENGTH ABSTRACT", SUBTITLE_FONT));
            List<SectionStrength> strengths = sectionStrengthRepository.findAll();
            if (!strengths.isEmpty()) {
                PdfPTable strengthTable = new PdfPTable(4);
                strengthTable.setWidthPercentage(100);
                addHeaderCell(strengthTable, "Section");
                addHeaderCell(strengthTable, "Designation");
                addHeaderCell(strengthTable, "Sanctioned");
                addHeaderCell(strengthTable, "Present");
                for (SectionStrength s : strengths) {
                    addCell(strengthTable, s.getSection());
                    addCell(strengthTable, s.getDesignation());
                    addCell(strengthTable, String.valueOf(s.getSanctionedCount()));
                    addCell(strengthTable, String.valueOf(s.getPresentCount()));
                }
                document.add(strengthTable);
            }
            document.add(new Paragraph(" "));

            // Section A - Fixed Duties
            document.add(new Paragraph("SECTION A - FIXED/ESSENTIAL DUTIES", SUBTITLE_FONT));
            addAssignmentTable(document, "A");

            // Section B - Office/Support
            document.add(new Paragraph("SECTION B - OFFICE/SUPPORT DUTIES", SUBTITLE_FONT));
            addAssignmentTable(document, "B");

            // Section C - Platoon Rotation
            document.add(new Paragraph("SECTION C - PLATOON ROTATION DUTIES", SUBTITLE_FONT));
            addAssignmentTable(document, "C");

            // Leave Summary
            document.add(new Paragraph("LEAVE SUMMARY", SUBTITLE_FONT));
            List<LeaveRequest> activeLeaves = leaveRequestRepository.findAll().stream()
                    .filter(lr -> "APPROVED".equals(lr.getStatus()))
                    .toList();
            PdfPTable leaveTable = new PdfPTable(3);
            leaveTable.setWidthPercentage(100);
            addHeaderCell(leaveTable, "Leave Type");
            addHeaderCell(leaveTable, "Count");
            addHeaderCell(leaveTable, "Status");
            String[] leaveTypes = {"LEAVE", "WEEKLY_OFF", "PERMISSION", "ABSENT", "SICK", "SUSPENSION"};
            for (String lt : leaveTypes) {
                long count = activeLeaves.stream().filter(l -> lt.equals(l.getLeaveType())).count();
                addCell(leaveTable, lt);
                addCell(leaveTable, String.valueOf(count));
                addCell(leaveTable, "APPROVED");
            }
            document.add(leaveTable);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating Form 168", e);
            throw new RuntimeException("Failed to generate Form 168 report", e);
        }
    }

    public byte[] generatePersonnelReport(PersonnelFilter filter) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, baos);
            document.open();

            Paragraph title = new Paragraph("PERSONNEL REPORT", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            List<Personnel> personnelList = personnelRepository.findAll();
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            addHeaderCell(table, "Badge ID");
            addHeaderCell(table, "Name");
            addHeaderCell(table, "Duty Type");
            addHeaderCell(table, "Section");
            addHeaderCell(table, "Designation");
            addHeaderCell(table, "Location");
            addHeaderCell(table, "Phone");
            addHeaderCell(table, "Active");

            for (Personnel p : personnelList) {
                addCell(table, p.getBadgeId());
                addCell(table, p.getPersonName());
                addCell(table, p.getDutyType() != null ? p.getDutyType() : "");
                addCell(table, p.getSection());
                addCell(table, p.getDesignation() != null ? p.getDesignation() : "");
                addCell(table, p.getLocation() != null ? p.getLocation() : "");
                addCell(table, p.getPhoneNumber() != null ? p.getPhoneNumber() : "");
                addCell(table, p.getIsActive() ? "Yes" : "No");
            }
            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating personnel report", e);
            throw new RuntimeException("Failed to generate personnel report", e);
        }
    }

    public byte[] generateScheduleReport() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, baos);
            document.open();

            Paragraph title = new Paragraph("SCHEDULE REPORT", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            for (String section : new String[]{"A", "B", "C"}) {
                document.add(new Paragraph("Section " + section, SUBTITLE_FONT));
                addAssignmentTable(document, section);
                document.add(new Paragraph(" "));
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating schedule report", e);
            throw new RuntimeException("Failed to generate schedule report", e);
        }
    }

    public byte[] generateLeaveReport(LeaveFilter filter) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 20, 20, 20, 20);
            PdfWriter.getInstance(document, baos);
            document.open();

            Paragraph title = new Paragraph("LEAVE REQUESTS REPORT", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            List<LeaveRequest> leaves = leaveRequestRepository.findAll();
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            addHeaderCell(table, "Personnel ID");
            addHeaderCell(table, "Leave Type");
            addHeaderCell(table, "Start Date");
            addHeaderCell(table, "End Date");
            addHeaderCell(table, "Status");
            addHeaderCell(table, "Reason");

            for (LeaveRequest lr : leaves) {
                addCell(table, String.valueOf(lr.getPersonnelId()));
                addCell(table, lr.getLeaveType());
                addCell(table, lr.getStartDate().toString());
                addCell(table, lr.getEndDate().toString());
                addCell(table, lr.getStatus());
                addCell(table, lr.getReason() != null ? lr.getReason() : "");
            }
            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating leave report", e);
            throw new RuntimeException("Failed to generate leave report", e);
        }
    }

    private void addAssignmentTable(Document document, String section) throws DocumentException {
        List<DutyAssignment> assignments = dutyAssignmentRepository.findBySectionAndIsCurrentTrue(section);
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        addHeaderCell(table, "Personnel ID");
        addHeaderCell(table, "Duty Type ID");
        addHeaderCell(table, "Shift");
        addHeaderCell(table, "Sub-Assignment");
        addHeaderCell(table, "Effective Date");

        for (DutyAssignment a : assignments) {
            addCell(table, String.valueOf(a.getPersonnelId()));
            addCell(table, String.valueOf(a.getDutyTypeId()));
            addCell(table, a.getShift() != null ? a.getShift() : "");
            addCell(table, a.getSubAssignment() != null ? a.getSubAssignment() : "");
            addCell(table, a.getEffectiveDate().toString());
        }
        document.add(table);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(new Color(0, 0, 128));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, CELL_FONT));
        cell.setPadding(4);
        table.addCell(cell);
    }
}
