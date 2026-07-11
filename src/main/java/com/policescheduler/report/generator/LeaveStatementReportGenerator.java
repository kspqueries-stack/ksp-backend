package com.policescheduler.report.generator;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.policescheduler.entity.LeaveRequest;
import com.policescheduler.entity.Personnel;
import com.policescheduler.report.PdfReportGenerator;
import com.policescheduler.report.PdfStyleHelper;
import com.policescheduler.report.ReportGenerationException;
import com.policescheduler.report.ReportLocalizationService;
import com.policescheduler.report.ReportRequest;
import com.policescheduler.repository.LeaveRequestRepository;
import com.policescheduler.repository.PersonnelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LeaveStatementReportGenerator implements PdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(LeaveStatementReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final LeaveRequestRepository leaveRequestRepository;
    private final PersonnelRepository personnelRepository;
    private final PdfStyleHelper pdfStyleHelper;
    private final ReportLocalizationService localizationService;

    public LeaveStatementReportGenerator(LeaveRequestRepository leaveRequestRepository,
                                          PersonnelRepository personnelRepository,
                                          PdfStyleHelper pdfStyleHelper,
                                          ReportLocalizationService localizationService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.personnelRepository = personnelRepository;
        this.pdfStyleHelper = pdfStyleHelper;
        this.localizationService = localizationService;
    }

    @Override
    public String getReportType() {
        return "leave_statement";
    }

    @Override
    public byte[] generate(ReportRequest request) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            String locale = request.getLocale() != null ? request.getLocale() : "en";
            LocalDate reportDate = request.getDate() != null ? request.getDate() : LocalDate.now();

            pdfStyleHelper.addTitle(document,
                    localizationService.getLabel("LEAVE STATEMENT", locale));
            pdfStyleHelper.addGenerationDate(document, reportDate);

            // Query leave requests with filters
            Specification<LeaveRequest> spec = buildSpecification(request);
            List<LeaveRequest> leaveRequests = leaveRequestRepository.findAll(spec);

            // Load personnel for names
            List<Long> personnelIds = leaveRequests.stream()
                    .map(LeaveRequest::getPersonnelId)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, Personnel> personnelMap = personnelRepository.findAllById(personnelIds)
                    .stream().collect(Collectors.toMap(Personnel::getId, p -> p));

            // Table: SL.NO | Personnel Name | Badge ID | Leave Type | Start Date | End Date | Status | Reason
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{6, 16, 10, 12, 12, 12, 10, 22});

            // Headers
            String[] headers = {"SL.NO", "Personnel Name", "Badge ID", "Leave Type",
                    "Start Date", "End Date", "Status", "Reason"};
            for (String header : headers) {
                table.addCell(pdfStyleHelper.createHeaderCell(
                        localizationService.getLabel(header, locale)));
            }

            int slNo = 1;
            for (LeaveRequest lr : leaveRequests) {
                Personnel p = personnelMap.get(lr.getPersonnelId());

                table.addCell(pdfStyleHelper.createCell(
                        String.valueOf(slNo++), Element.ALIGN_CENTER));
                table.addCell(pdfStyleHelper.createCell(
                        p != null ? p.getPersonName() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        p != null ? p.getBadgeId() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        lr.getLeaveType() != null ? lr.getLeaveType() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        lr.getStartDate() != null ? lr.getStartDate().format(DATE_FORMAT) : ""));
                table.addCell(pdfStyleHelper.createCell(
                        lr.getEndDate() != null ? lr.getEndDate().format(DATE_FORMAT) : ""));
                table.addCell(pdfStyleHelper.createCell(
                        lr.getStatus() != null ? lr.getStatus() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        lr.getReason() != null ? lr.getReason() : ""));
            }

            if (leaveRequests.isEmpty()) {
                PdfPCell noDataCell = pdfStyleHelper.createCell(
                        "No leave records found", Element.ALIGN_CENTER);
                noDataCell.setColspan(8);
                table.addCell(noDataCell);
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate leave statement report", e);
            throw new ReportGenerationException("leave_statement",
                    "Failed to generate leave statement report", e);
        }
    }

    private Specification<LeaveRequest> buildSpecification(ReportRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getLeaveStatus() != null && !request.getLeaveStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"), request.getLeaveStatus()));
            }
            if (request.getLeaveType() != null && !request.getLeaveType().isBlank()) {
                predicates.add(cb.equal(root.get("leaveType"), request.getLeaveType()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
