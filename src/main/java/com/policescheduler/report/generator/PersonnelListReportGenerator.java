package com.policescheduler.report.generator;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PersonnelListReportGenerator implements PdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(PersonnelListReportGenerator.class);

    private final PersonnelRepository personnelRepository;
    private final PdfStyleHelper pdfStyleHelper;
    private final ReportLocalizationService localizationService;

    public PersonnelListReportGenerator(PersonnelRepository personnelRepository,
                                         PdfStyleHelper pdfStyleHelper,
                                         ReportLocalizationService localizationService) {
        this.personnelRepository = personnelRepository;
        this.pdfStyleHelper = pdfStyleHelper;
        this.localizationService = localizationService;
    }

    @Override
    public String getReportType() {
        return "personnel_list";
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
                    localizationService.getLabel("PERSONNEL LIST", locale));
            pdfStyleHelper.addGenerationDate(document, reportDate);

            // Build specification from filters
            Specification<Personnel> spec = buildSpecification(request);
            List<Personnel> personnel = personnelRepository.findAll(spec);

            // Table: SL.NO | Badge ID | Name | Designation | Section | Duty Type | Location | Phone | Status
            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{6, 10, 16, 10, 8, 12, 14, 12, 8});

            // Headers
            String[] headers = {"SL.NO", "Badge ID", "Name", "Designation", "Section",
                    "Duty Type", "Location", "Phone", "Status"};
            for (String header : headers) {
                table.addCell(pdfStyleHelper.createHeaderCell(
                        localizationService.getLabel(header, locale)));
            }

            int slNo = 1;
            for (Personnel p : personnel) {
                table.addCell(pdfStyleHelper.createCell(
                        String.valueOf(slNo++), Element.ALIGN_CENTER));
                table.addCell(pdfStyleHelper.createCell(
                        p.getBadgeId() != null ? p.getBadgeId() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        p.getPersonName() != null ? p.getPersonName() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        p.getDesignation() != null ? p.getDesignation() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        p.getSection() != null ? p.getSection() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        p.getDutyType() != null ? p.getDutyType() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        p.getLocation() != null ? p.getLocation() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        p.getPhoneNumber() != null ? p.getPhoneNumber() : ""));
                table.addCell(pdfStyleHelper.createCell(
                        Boolean.TRUE.equals(p.getIsActive()) ? "Active" : "Inactive"));
            }

            if (personnel.isEmpty()) {
                com.lowagie.text.pdf.PdfPCell noDataCell = pdfStyleHelper.createCell(
                        "No personnel found", Element.ALIGN_CENTER);
                noDataCell.setColspan(9);
                table.addCell(noDataCell);
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate personnel list report", e);
            throw new ReportGenerationException("personnel_list",
                    "Failed to generate personnel list report", e);
        }
    }

    private Specification<Personnel> buildSpecification(ReportRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getSection() != null && !request.getSection().isBlank()) {
                predicates.add(cb.equal(root.get("section"), request.getSection()));
            }
            if (request.getDesignation() != null && !request.getDesignation().isBlank()) {
                predicates.add(cb.equal(root.get("designation"), request.getDesignation()));
            }
            if (request.getDutyType() != null && !request.getDutyType().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("dutyType")),
                        request.getDutyType().toLowerCase()));
            }
            if (request.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), request.getIsActive()));
            } else if (predicates.isEmpty()) {
                // Default: all active personnel when no filters
                predicates.add(cb.equal(root.get("isActive"), true));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
