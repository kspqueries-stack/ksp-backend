package com.policescheduler.service;

import com.policescheduler.dto.CycleCreateRequest;
import com.policescheduler.dto.PlatoonSectionMapping;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates cycle creation requests, enforcing business rules for rotation days,
 * platoon-section mappings, and date range calculations.
 */
@Service
public class CycleValidator {

    /**
     * Validates the given cycle creation request and calculates the end date.
     *
     * @param request the cycle creation request to validate
     * @return the calculated end date (startDate + rotationDays - 1)
     * @throws IllegalArgumentException if any validation rule is violated
     */
    public LocalDate validateAndCalculateEndDate(CycleCreateRequest request) {
        validateRotationDays(request.rotationDays());
        validatePlatoonSections(request.platoonSections());
        validateNoDuplicatePlatoonSectionPairs(request.platoonSections());

        LocalDate endDate = request.startDate().plusDays(request.rotationDays() - 1);

        return endDate;
    }

    private void validateRotationDays(Integer rotationDays) {
        if (rotationDays == null || rotationDays <= 0) {
            throw new IllegalArgumentException("Rotation days must be a positive integer");
        }
    }

    private void validatePlatoonSections(List<PlatoonSectionMapping> platoonSections) {
        if (platoonSections == null || platoonSections.size() != 5) {
            throw new IllegalArgumentException(
                "Each of the 5 platoons must have at least one section assigned");
        }

        for (PlatoonSectionMapping mapping : platoonSections) {
            if (mapping.sectionIds() == null || mapping.sectionIds().isEmpty()) {
                throw new IllegalArgumentException(
                    "Each of the 5 platoons must have at least one section assigned");
            }
        }
    }

    private void validateNoDuplicatePlatoonSectionPairs(List<PlatoonSectionMapping> platoonSections) {
        Set<String> seen = new HashSet<>();

        for (PlatoonSectionMapping mapping : platoonSections) {
            for (Long sectionId : mapping.sectionIds()) {
                String key = mapping.platoonId() + "-" + sectionId;
                if (!seen.add(key)) {
                    throw new IllegalArgumentException(
                        "Duplicate section assignment for platoon");
                }
            }
        }
    }
}
