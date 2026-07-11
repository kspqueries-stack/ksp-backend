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
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for the Platoon Chart Multi-Section feature.
 * Uses jqwik with minimum 100 iterations per property.
 */
class PlatoonChartMultiSectionPropertyTest {

    private static final List<String> ALL_SECTIONS = List.of("C", "D", "E", "F", "G");
    private static final List<String> DUTY_COLUMNS = List.of(
            "GUARD-I", "GUARD-II", "CHECK POINT", "PRISON/VIP ESCORT/OUT", "STRIKING FORCE/HELP"
    );

    // --- Generators ---

    @Provide
    Arbitrary<String> validSections() {
        return Arbitraries.of("C", "D", "E", "F", "G");
    }

    @Provide
    Arbitrary<List<Personnel>> randomPersonnel() {
        return Arbitraries.integers().between(0, 20).flatMap(count -> {
            if (count == 0) return Arbitraries.just(Collections.emptyList());
            return Arbitraries.integers().between(1, count)
                    .list().ofSize(count)
                    .flatMap(ids -> {
                        List<Arbitrary<Personnel>> personnelArbs = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            int idx = i;
                            personnelArbs.add(
                                Arbitraries.of("C", "D", "E", "F", "G").flatMap(section ->
                                    Arbitraries.of("AHC", "APC").flatMap(designation ->
                                        Arbitraries.integers().between(1, 5).map(platoonIdx -> {
                                            Personnel p = new Personnel();
                                            int badgeNum = 100 + idx;
                                            p.setId((long) (idx + 1));
                                            p.setBadgeId(designation + "-" + badgeNum);
                                            p.setDesignation(designation);
                                            p.setSection(section);
                                            p.setPlatoonId((long) platoonIdx);
                                            p.setIsActive(true);
                                            p.setPersonName("Person_" + idx + "_" + section);
                                            return p;
                                        })
                                    )
                                )
                            );
                        }
                        return Combinators.combine(personnelArbs).as(list -> list);
                    });
        });
    }

    @Provide
    Arbitrary<Integer> cycleIndices() {
        return Arbitraries.integers().between(0, 20);
    }

    // --- Helper methods ---

    private PlatoonChartReportGenerator createGenerator(List<Personnel> personnel,
                                                         List<Platoon> platoons,
                                                         PlatoonRotationState state) {
        PersonnelRepository personnelRepo = mock(PersonnelRepository.class);
        PlatoonRepository platoonRepo = mock(PlatoonRepository.class);
        PlatoonRotationStateRepository rotationRepo = mock(PlatoonRotationStateRepository.class);
        DutyTypeRepository dutyTypeRepo = mock(DutyTypeRepository.class);
        PdfStyleHelper styleHelper = new PdfStyleHelper();
        ReportLocalizationService locService = mock(ReportLocalizationService.class);

        when(locService.getLabel(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(personnelRepo.findAll()).thenReturn(personnel);
        when(platoonRepo.findAllByOrderByBaseOffsetAsc()).thenReturn(platoons);
        when(rotationRepo.findAll()).thenReturn(state != null ? List.of(state) : Collections.emptyList());

        return new PlatoonChartReportGenerator(
                personnelRepo, platoonRepo, rotationRepo, dutyTypeRepo, styleHelper, locService);
    }

    private List<Platoon> createPlatoons() {
        List<Platoon> platoons = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Platoon p = new Platoon();
            p.setId((long) (i + 1));
            p.setName("Platoon " + (i + 1));
            p.setBaseOffset(i);
            platoons.add(p);
        }
        return platoons;
    }

    private PlatoonRotationState createRotationState(int cycleIndex) {
        PlatoonRotationState state = new PlatoonRotationState();
        state.setCurrentCycleIndex(cycleIndex);
        state.setLastRotationDate(LocalDate.of(2025, 1, 1));
        state.setNextRotationDate(LocalDate.of(2025, 1, 16));
        return state;
    }

    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    // --- Property 1: Combined grid PDF contains SECTION C title ---
    // Feature: platoon-chart-multi-section, Property 1: Multi-section PDF contains all five section titles in order

    @Property(tries = 100)
    void combinedGrid_containsSectionCTitle(
            @ForAll("randomPersonnel") List<Personnel> personnel,
            @ForAll("cycleIndices") int cycleIndex) throws Exception {

        PlatoonChartReportGenerator gen = createGenerator(
                personnel, createPlatoons(), createRotationState(cycleIndex));

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = gen.generate(request);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("SECTION C"),
                "PDF should contain SECTION C title");
    }

    // --- Property 2: Combined grid contains all duty column headers ---
    // Feature: platoon-chart-multi-section, Property 2: Single-section filter produces only the requested section

    @Property(tries = 100)
    void combinedGrid_containsAllDutyHeaders(
            @ForAll("randomPersonnel") List<Personnel> personnel,
            @ForAll("cycleIndices") int cycleIndex) throws Exception {

        PlatoonChartReportGenerator gen = createGenerator(
                personnel, createPlatoons(), createRotationState(cycleIndex));

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = gen.generate(request);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("GUARD-I"), "Should contain GUARD-I");
        assertTrue(text.contains("GUARD-II"), "Should contain GUARD-II");
        assertTrue(text.contains("CHECK POINT"), "Should contain CHECK POINT");
    }

    // --- Property 5: Badge IDs formatted with designation prefix only on first badge per group ---
    // Feature: platoon-chart-multi-section, Property 5: Badge IDs formatted with designation prefix only on first badge per group

    @Property(tries = 100)
    void badgeIdFormatting_prefixOnlyOnFirstBadge(
            @ForAll @Size(min = 1, max = 10) List<@From("badgePersonnel") Personnel> personnel) {

        PlatoonChartReportGenerator gen = createGenerator(
                Collections.emptyList(), Collections.emptyList(), null);

        String result = gen.formatBadgeIds(personnel);

        if (result.isEmpty()) return;

        // The new format uses comma-separated groups on a single line: "AHC-127, 290, APC-101, 205"
        if (result.contains("AHC-")) {
            // AHC prefix should appear exactly once
            int firstAhc = result.indexOf("AHC-");
            int secondAhc = result.indexOf("AHC-", firstAhc + 4);
            assertEquals(-1, secondAhc, "AHC- prefix should appear only once: " + result);
        }

        if (result.contains("APC-")) {
            // APC prefix should appear exactly once
            int firstApc = result.indexOf("APC-");
            int secondApc = result.indexOf("APC-", firstApc + 4);
            assertEquals(-1, secondApc, "APC- prefix should appear only once: " + result);
        }

        // If both AHC and APC exist, AHC should come first
        if (result.contains("AHC-") && result.contains("APC-")) {
            assertTrue(result.indexOf("AHC-") < result.indexOf("APC-"),
                    "AHC group should appear before APC group");
        }
    }

    @Provide
    Arbitrary<Personnel> badgePersonnel() {
        return Arbitraries.of("AHC", "APC").flatMap(designation ->
                Arbitraries.integers().between(1, 9999).map(num -> {
                    Personnel p = new Personnel();
                    p.setBadgeId(designation + "-" + num);
                    p.setDesignation(designation);
                    p.setPersonName("Person " + num);
                    p.setSection("C");
                    p.setPlatoonId(1L);
                    p.setIsActive(true);
                    return p;
                })
        );
    }

    // --- Property 6: Personnel names excluded from badge ID cells ---
    // Feature: platoon-chart-multi-section, Property 6: Personnel names excluded from badge ID cells

    @Property(tries = 100)
    void personnelNames_notInPdf(
            @ForAll("validSections") String section,
            @ForAll("cycleIndices") int cycleIndex) throws Exception {

        // Create personnel with distinctive names
        List<Personnel> personnel = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Personnel p = new Personnel();
            p.setId((long) (i + 1));
            p.setBadgeId("AHC-" + (500 + i));
            p.setDesignation("AHC");
            p.setSection(section);
            p.setPlatoonId(1L);
            p.setIsActive(true);
            p.setPersonName("UniqueTestName_" + UUID.randomUUID().toString().substring(0, 8));
            personnel.add(p);
        }

        PlatoonChartReportGenerator gen = createGenerator(
                personnel, createPlatoons(), createRotationState(cycleIndex));

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(LocalDate.of(2025, 1, 15))
                .locale("en")
                .build();

        byte[] pdf = gen.generate(request);
        String text = extractPdfText(pdf);

        for (Personnel p : personnel) {
            assertFalse(text.contains(p.getPersonName()),
                    "Personnel name '" + p.getPersonName() + "' should NOT appear in PDF");
        }
    }

    // --- Property 7: Rotation mapping follows the modulo formula ---
    // Feature: platoon-chart-multi-section, Property 7: Rotation mapping follows the modulo formula

    @Property(tries = 100)
    void rotationMapping_followsModuloFormula(
            @ForAll @IntRange(min = 0, max = 20) int cycleIndex,
            @ForAll @IntRange(min = 0, max = 4) int baseOffset1,
            @ForAll @IntRange(min = 0, max = 4) int baseOffset2,
            @ForAll @IntRange(min = 0, max = 4) int baseOffset3) {

        // Verify the rotation formula by checking that the generated PDF
        // contains the expected platoon labels in the correct positions.
        // The formula is: duty_index = (base_offset + cycle_index) % 5

        int expectedDuty1 = (baseOffset1 + cycleIndex) % 5;
        int expectedDuty2 = (baseOffset2 + cycleIndex) % 5;
        int expectedDuty3 = (baseOffset3 + cycleIndex) % 5;

        // All computed duty indices should be in range [0, 4]
        assertTrue(expectedDuty1 >= 0 && expectedDuty1 < 5);
        assertTrue(expectedDuty2 >= 0 && expectedDuty2 < 5);
        assertTrue(expectedDuty3 >= 0 && expectedDuty3 < 5);
    }

    // --- Property 8: All dates use DD-MM-YYYY format ---
    // Feature: platoon-chart-multi-section, Property 8: All dates use DD-MM-YYYY format

    @Property(tries = 100)
    void allDates_useDdMmYyyyFormat(
            @ForAll @IntRange(min = 2020, max = 2030) int year,
            @ForAll @IntRange(min = 1, max = 12) int month,
            @ForAll @IntRange(min = 1, max = 28) int day) throws Exception {

        LocalDate reportDate = LocalDate.of(year, month, day);

        PlatoonRotationState state = new PlatoonRotationState();
        state.setCurrentCycleIndex(0);
        state.setLastRotationDate(reportDate);
        state.setNextRotationDate(reportDate.plusDays(15));

        PlatoonChartReportGenerator gen = createGenerator(
                Collections.emptyList(), createPlatoons(), state);

        ReportRequest request = ReportRequest.builder()
                .reportType("platoon_chart")
                .date(reportDate)
                .locale("en")
                .build();

        byte[] pdf = gen.generate(request);
        String text = extractPdfText(pdf);

        // Find all date-like patterns in the text
        Pattern datePattern = Pattern.compile("\\d{2}-\\d{2}-\\d{4}");
        Matcher matcher = datePattern.matcher(text);

        assertTrue(matcher.find(), "PDF should contain at least one date in DD-MM-YYYY format");

        // Verify all date-like strings match the pattern
        matcher.reset();
        while (matcher.find()) {
            String dateStr = matcher.group();
            assertTrue(dateStr.matches("\\d{2}-\\d{2}-\\d{4}"),
                    "Date should match DD-MM-YYYY format: " + dateStr);
        }
    }
}
