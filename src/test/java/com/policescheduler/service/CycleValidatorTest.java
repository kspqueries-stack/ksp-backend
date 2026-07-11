package com.policescheduler.service;

import com.policescheduler.dto.CycleCreateRequest;
import com.policescheduler.dto.PlatoonSectionMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CycleValidatorTest {

    private CycleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CycleValidator();
    }

    @Test
    void validateAndCalculateEndDate_validRequest_returnsCorrectEndDate() {
        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 1, 1),
            7,
            createValidPlatoonSections()
        );

        LocalDate endDate = validator.validateAndCalculateEndDate(request);

        assertEquals(LocalDate.of(2024, 1, 7), endDate);
    }

    @Test
    void validateAndCalculateEndDate_singleRotationDay_returnsStartDate() {
        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 6, 15),
            1,
            createValidPlatoonSections()
        );

        LocalDate endDate = validator.validateAndCalculateEndDate(request);

        assertEquals(LocalDate.of(2024, 6, 15), endDate);
    }

    @Test
    void validateAndCalculateEndDate_rotationDaysZero_throwsException() {
        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 1, 1),
            0,
            createValidPlatoonSections()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndCalculateEndDate(request));
        assertEquals("Rotation days must be a positive integer", ex.getMessage());
    }

    @Test
    void validateAndCalculateEndDate_rotationDaysNegative_throwsException() {
        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 1, 1),
            -5,
            createValidPlatoonSections()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndCalculateEndDate(request));
        assertEquals("Rotation days must be a positive integer", ex.getMessage());
    }

    @Test
    void validateAndCalculateEndDate_fewerThan5Platoons_throwsException() {
        List<PlatoonSectionMapping> platoons = List.of(
            new PlatoonSectionMapping(1L, List.of(1L)),
            new PlatoonSectionMapping(2L, List.of(2L)),
            new PlatoonSectionMapping(3L, List.of(3L))
        );

        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 1, 1), 7, platoons
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndCalculateEndDate(request));
        assertEquals("Each of the 5 platoons must have at least one section assigned", ex.getMessage());
    }

    @Test
    void validateAndCalculateEndDate_moreThan5Platoons_throwsException() {
        List<PlatoonSectionMapping> platoons = List.of(
            new PlatoonSectionMapping(1L, List.of(1L)),
            new PlatoonSectionMapping(2L, List.of(2L)),
            new PlatoonSectionMapping(3L, List.of(3L)),
            new PlatoonSectionMapping(4L, List.of(4L)),
            new PlatoonSectionMapping(5L, List.of(5L)),
            new PlatoonSectionMapping(6L, List.of(6L))
        );

        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 1, 1), 7, platoons
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndCalculateEndDate(request));
        assertEquals("Each of the 5 platoons must have at least one section assigned", ex.getMessage());
    }

    @Test
    void validateAndCalculateEndDate_platoonWithEmptySections_throwsException() {
        List<PlatoonSectionMapping> platoons = List.of(
            new PlatoonSectionMapping(1L, List.of(1L)),
            new PlatoonSectionMapping(2L, List.of(2L)),
            new PlatoonSectionMapping(3L, List.of()),
            new PlatoonSectionMapping(4L, List.of(4L)),
            new PlatoonSectionMapping(5L, List.of(5L))
        );

        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 1, 1), 7, platoons
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndCalculateEndDate(request));
        assertEquals("Each of the 5 platoons must have at least one section assigned", ex.getMessage());
    }

    @Test
    void validateAndCalculateEndDate_duplicatePlatoonSectionPair_throwsException() {
        List<PlatoonSectionMapping> platoons = List.of(
            new PlatoonSectionMapping(1L, List.of(1L, 1L)),
            new PlatoonSectionMapping(2L, List.of(2L)),
            new PlatoonSectionMapping(3L, List.of(3L)),
            new PlatoonSectionMapping(4L, List.of(4L)),
            new PlatoonSectionMapping(5L, List.of(5L))
        );

        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 1, 1), 7, platoons
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndCalculateEndDate(request));
        assertEquals("Duplicate section assignment for platoon", ex.getMessage());
    }

    @Test
    void validateAndCalculateEndDate_multipleSectionsPerPlatoon_valid() {
        List<PlatoonSectionMapping> platoons = List.of(
            new PlatoonSectionMapping(1L, List.of(1L, 2L)),
            new PlatoonSectionMapping(2L, List.of(3L, 4L)),
            new PlatoonSectionMapping(3L, List.of(5L)),
            new PlatoonSectionMapping(4L, List.of(6L, 7L)),
            new PlatoonSectionMapping(5L, List.of(8L))
        );

        CycleCreateRequest request = new CycleCreateRequest(
            LocalDate.of(2024, 3, 1), 14, platoons
        );

        LocalDate endDate = validator.validateAndCalculateEndDate(request);

        assertEquals(LocalDate.of(2024, 3, 14), endDate);
    }

    private List<PlatoonSectionMapping> createValidPlatoonSections() {
        return List.of(
            new PlatoonSectionMapping(1L, List.of(1L)),
            new PlatoonSectionMapping(2L, List.of(2L)),
            new PlatoonSectionMapping(3L, List.of(3L)),
            new PlatoonSectionMapping(4L, List.of(4L)),
            new PlatoonSectionMapping(5L, List.of(5L))
        );
    }
}
