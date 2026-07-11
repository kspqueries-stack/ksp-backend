package com.policescheduler.report.generator;

import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.Platoon;
import com.policescheduler.entity.PlatoonRotationState;
import com.policescheduler.report.PdfStyleHelper;
import com.policescheduler.report.ReportLocalizationService;
import com.policescheduler.report.ReportRequest;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.PlatoonRepository;
import com.policescheduler.repository.PlatoonRotationStateRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatoonChartReportGeneratorTest {

    private PersonnelRepository personnelRepository;
    private PlatoonRepository platoonRepository;
    private PlatoonRotationStateRepository rotationStateRepository;
    private DutyTypeRepository dutyTypeRepository;
    private PdfStyleHelper pdfStyleHelper;
    private ReportLocalizationService localizationService;
    private PlatoonChartReportGenerator generator;

    @BeforeEach
    void setUp() {
        personnelRepository = mock(PersonnelRepository.class);
        platoonRepository = mock(PlatoonRepository.class);
        rotationStateRepository = mock(PlatoonRotationStateRepository.class);
        dutyTypeRepository = mock(DutyTypeRepository.class);
        pdfStyleHelper = new PdfStyleHelper();
        localizationService = mock(ReportLocalizationService.class);

        // Default: localization returns the key as-is (English)
        when(localizationService.getLabel(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        generator = new PlatoonChartReportGenerator(
                personnelRepository, platoonRepository, rotationStateRepository,
                dutyTypeRepository, pdfStyleHelper, localizationService);
    }

    // --- Badge formatting tests ---

    @Test
    void formatBadgeIds_mixedAhcApc_groupsCorrectly() {
        List<Personnel> personnel = List.of(
                createPersonnel("AHC-127", "AHC"),
                createPersonnel("AHC-290", "AHC"),
                createPersonnel("APC-101", "APC"),
                createPersonnel("APC-205", "APC")
        );

        String result = generator.formatBadgeIds(personnel);

        assertEquals("AHC-127, 290, APC-101, 205", result);
    }

    @Test
    void formatBadgeIds_onlyAhc_noApcGroup() {
        List<Personnel> personnel = List.of(
                createPersonnel("AHC-100", "AHC"),
                createPersonnel("AHC-200", "AHC")
        );

        String result = generator.formatBadgeIds(personnel);

        assertEquals("AHC-100, 200", result);
    }

    @Test
    void formatBadgeIds_onlyApc_noAhcGroup() {
        List<Personnel> personnel = List.of(
                createPersonnel("APC-50", "APC"),
                createPersonnel("APC-60", "APC")
        );

        String result = generator.formatBadgeIds(personnel);

        assertEquals("APC-50, 60", result);
    }

    @Test
    void formatBadgeIds_singleAhc() {
        List<Personnel> personnel = List.of(
                createPersonnel("AHC-999", "AHC")
        );

        String result = generator.formatBadgeIds(personnel);

        assertEquals("AHC-999", result);
    }

    @Test
    void formatBadgeIds_emptyList_returnsEmpty() {
        String result = generator.formatBadgeIds(Collections.emptyList());

        assertEquals("", result);
    }

    @Test
    void formatBadgeIds_ahcBeforeApc() {
        // Even if APC personnel come first in the list, AHC should appear first in output
        List<Personnel> personnel = List.of(
                createPersonnel("APC-10", "APC"),
                createPersonnel("AHC-20", "AHC")
        );

        String result = generator.formatBadgeIds(personnel);

        assertTrue(result.startsWith("AHC-"));
        assertTrue(result.contains("APC-"));
        assertTrue(result.indexOf("AHC-") < result.indexOf("APC-"));
    }

    // --- extractBadgeNumber tests ---

    @Test
    void extractBadgeNumber_withPrefix() {
        assertEquals("127", generator.extractBadgeNumber("AHC-127"));
        assertEquals("101", generator.extractBadgeNumber("APC-101"));
    }

    @Test
    void extractBadgeNumber_withoutPrefix() {
        assertEquals("127", generator.extractBadgeNumber("127"));
    }

    @Test
    void extractBadgeNumber_null() {
        assertEquals("", generator.extractBadgeNumber(null));
    }

    @Test
    void extractBadgeNumber_multipleHyphens() {
        // "X-AHC-127" should extract "127" (after last hyphen)
        assertEquals("127", generator.extractBadgeNumber("X-AHC-127"));
    }

    // --- PDF generation integration tests ---

    @Test
    void generate_producesSinglePageWithSectionCTitle() throws Exception {
        setupMinimalMocks();

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = generator.generate(request);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("SECTION C"), "Should contain SECTION C title");
    }

    @Test
    void generate_containsDutyColumnHeaders() throws Exception {
        setupMinimalMocks();

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = generator.generate(request);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("GUARD-I"), "Should contain GUARD-I header");
        assertTrue(text.contains("GUARD-II"), "Should contain GUARD-II header");
        assertTrue(text.contains("CHECK POINT"), "Should contain CHECK POINT header");
    }

    @Test
    void generate_containsDateColumn() throws Exception {
        setupMinimalMocks();

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = generator.generate(request);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("DATE"), "Should contain DATE header");
    }

    @Test
    void generate_withPlatoons_containsPlatoonLabels() throws Exception {
        Platoon platoon = createPlatoon(1L, "Platoon I", 0);
        when(platoonRepository.findAllByOrderByBaseOffsetAsc()).thenReturn(List.of(platoon));
        when(personnelRepository.findAll()).thenReturn(Collections.emptyList());

        PlatoonRotationState state = new PlatoonRotationState();
        state.setCurrentCycleIndex(0);
        state.setLastRotationDate(LocalDate.of(2025, 1, 1));
        when(rotationStateRepository.findAll()).thenReturn(List.of(state));

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = generator.generate(request);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("PLATOON-I"), "Should contain PLATOON-I label");
    }

    @Test
    void generate_showsMultipleRotationPeriods() throws Exception {
        Platoon platoon = createPlatoon(1L, "Platoon I", 0);
        when(platoonRepository.findAllByOrderByBaseOffsetAsc()).thenReturn(List.of(platoon));
        when(personnelRepository.findAll()).thenReturn(Collections.emptyList());

        PlatoonRotationState state = new PlatoonRotationState();
        state.setCurrentCycleIndex(0);
        state.setLastRotationDate(LocalDate.of(2025, 1, 1));
        when(rotationStateRepository.findAll()).thenReturn(List.of(state));

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = generator.generate(request);
        String text = extractPdfText(pdf);

        // Should contain multiple date ranges (5 rotation periods)
        assertTrue(text.contains("01-01-2025"), "Should contain first period start date");
        assertTrue(text.contains("16-01-2025"), "Should contain second period start date");
    }

    @Test
    void generate_noRotationState_usesFallbackDates() throws Exception {
        when(personnelRepository.findAll()).thenReturn(Collections.emptyList());
        when(platoonRepository.findAllByOrderByBaseOffsetAsc()).thenReturn(Collections.emptyList());
        when(rotationStateRepository.findAll()).thenReturn(Collections.emptyList());

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 6, 1))
                .locale("en")
                .build();

        byte[] pdf = generator.generate(request);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0, "PDF should be generated even without rotation state");
    }

    @Test
    void generate_personnelNamesNotInCells() throws Exception {
        Personnel p = createPersonnel("AHC-100", "AHC");
        p.setPersonName("John Doe Unique Name");
        p.setSection("C");
        p.setPlatoonId(1L);
        p.setIsActive(true);

        when(personnelRepository.findAll()).thenReturn(List.of(p));

        Platoon platoon = createPlatoon(1L, "Platoon I", 0);
        when(platoonRepository.findAllByOrderByBaseOffsetAsc()).thenReturn(List.of(platoon));

        PlatoonRotationState state = new PlatoonRotationState();
        state.setCurrentCycleIndex(0);
        state.setLastRotationDate(LocalDate.of(2025, 1, 1));
        when(rotationStateRepository.findAll()).thenReturn(List.of(state));

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = generator.generate(request);
        String text = extractPdfText(pdf);

        assertFalse(text.contains("John Doe Unique Name"),
                "Personnel names should NOT appear in the PDF");
    }

    // --- Helper methods ---

    private void setupMinimalMocks() {
        when(personnelRepository.findAll()).thenReturn(Collections.emptyList());
        when(platoonRepository.findAllByOrderByBaseOffsetAsc()).thenReturn(Collections.emptyList());
        when(rotationStateRepository.findAll()).thenReturn(Collections.emptyList());
    }

    private Personnel createPersonnel(String badgeId, String designation) {
        Personnel p = new Personnel();
        p.setBadgeId(badgeId);
        p.setDesignation(designation);
        p.setPersonName("Test Person");
        p.setSection("C");
        p.setPlatoonId(1L);
        p.setIsActive(true);
        return p;
    }

    private Platoon createPlatoon(Long id, String name, int baseOffset) {
        Platoon p = new Platoon();
        p.setId(id);
        p.setName(name);
        p.setBaseOffset(baseOffset);
        return p;
    }

    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }
}
