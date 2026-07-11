package com.policescheduler.report;

import com.policescheduler.entity.DutyType;
import com.policescheduler.entity.Personnel;
import com.policescheduler.report.dto.*;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.PersonnelRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportDataService {

    private static final List<String> DESIGNATION_COLUMNS =
            List.of("DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC");

    private final DutyTypeRepository dutyTypeRepository;
    private final PersonnelRepository personnelRepository;

    public ReportDataService(DutyTypeRepository dutyTypeRepository,
                             PersonnelRepository personnelRepository) {
        this.dutyTypeRepository = dutyTypeRepository;
        this.personnelRepository = personnelRepository;
    }

    public SectionReportData buildSectionData(String section) {
        List<DutyType> topLevelTypes = dutyTypeRepository.findBySectionAndParentIsNullOrderBySortOrderAsc(section);
        List<Personnel> sectionPersonnel = personnelRepository.findBySectionAndIsActiveTrue(section);

        // Track assigned personnel to prevent duplicates
        Set<Long> assignedPersonnelIds = new HashSet<>();

        List<DutyCategoryRow> rows = new ArrayList<>();
        int[] grandTotals = new int[7];
        int serialNumber = 1;

        for (DutyType category : topLevelTypes) {
            // Get children (sub-duty types)
            List<DutyType> children = dutyTypeRepository.findByParentIdOrderBySortOrderAsc(category.getId());

            List<SubDutyRow> subRows = new ArrayList<>();
            int[] categoryCounts = new int[7];

            if (children.isEmpty()) {
                // No sub-categories — match personnel to this category
                List<Personnel> categoryPersonnel = matchPersonnelToCategory(
                        sectionPersonnel, category, assignedPersonnelIds);

                for (Personnel p : categoryPersonnel) {
                    incrementDesignationCount(categoryCounts, p.getDesignation());
                }

                List<PersonnelEntry> categoryEntries = categoryPersonnel.stream()
                        .map(this::toPersonnelEntry)
                        .collect(Collectors.toList());

                rows.add(new DutyCategoryRow(serialNumber, category.getName(), categoryEntries, categoryCounts, subRows));
            } else {
                // Has sub-categories — match personnel to each child
                List<PersonnelEntry> parentEntries = new ArrayList<>();

                for (DutyType child : children) {
                    List<Personnel> childPersonnel = matchPersonnelToCategory(
                            sectionPersonnel, child, assignedPersonnelIds);

                    int[] subCounts = new int[7];
                    for (Personnel p : childPersonnel) {
                        incrementDesignationCount(subCounts, p.getDesignation());
                        incrementDesignationCount(categoryCounts, p.getDesignation());
                    }

                    List<PersonnelEntry> childEntries = childPersonnel.stream()
                            .map(this::toPersonnelEntry)
                            .collect(Collectors.toList());

                    subRows.add(new SubDutyRow(child.getName(), childEntries, subCounts));
                }

                rows.add(new DutyCategoryRow(serialNumber, category.getName(), parentEntries, categoryCounts, subRows));
            }

            // Add to grand totals
            for (int i = 0; i < 7; i++) {
                grandTotals[i] += categoryCounts[i];
            }

            serialNumber++;
        }

        return new SectionReportData("SECTION " + section, rows, grandTotals);
    }

    /**
     * Matches personnel to a duty type category.
     * Uses FK match first, then falls back to text matching.
     * Tracks assigned IDs to prevent the same person appearing in multiple categories.
     */
    private List<Personnel> matchPersonnelToCategory(List<Personnel> allPersonnel, DutyType dutyType, Set<Long> assignedIds) {
        String categoryName = dutyType.getName().toUpperCase().trim();

        List<Personnel> matched = allPersonnel.stream()
                .filter(p -> !assignedIds.contains(p.getId()))  // Skip already assigned
                .filter(p -> {
                    // Strategy 1: FK match (new data — most reliable)
                    if (p.getDutyTypeEntity() != null) {
                        return p.getDutyTypeEntity().getId().equals(dutyType.getId());
                    }
                    // Strategy 2: Fallback text match (old data without FK)
                    String pDutyType = p.getDutyType() != null ? p.getDutyType().toUpperCase().trim() : "";
                    String pDutyLocation = p.getDutyLocation() != null ? p.getDutyLocation().toUpperCase().trim() : "";
                    String pDesignation = p.getDesignation() != null ? p.getDesignation().toUpperCase().trim() : "";

                    // Exact match on duty_type or duty_location
                    if (pDutyType.equals(categoryName) || pDutyLocation.equals(categoryName)) {
                        return true;
                    }

                    // For simple designation-only category names like "DCP, ACP, RPI" 
                    // (not descriptive names that happen to contain commas like "OFFICE WRITER (DCP, ACP, RPI) / COMPUTER OPERATOR")
                    if (categoryName.contains(",") && !categoryName.contains("/") && !categoryName.contains("OFFICE")
                            && !categoryName.contains("TEAM") && !categoryName.contains("DUTY")
                            && categoryName.length() < 20) {
                        String[] parts = categoryName.split(",");
                        for (String part : parts) {
                            String trimmed = part.trim().replaceAll("[^A-Z]", "");
                            if (!trimmed.isEmpty() && pDesignation.contains(trimmed)) {
                                return true;
                            }
                        }
                    }

                    // For categories with parentheses like "RSI(DUTY OFFICER), ARSI (ADO)"
                    // Check if designation matches the key part
                    if (categoryName.contains("(")) {
                        String keyPart = categoryName.split("\\(")[0].trim().replaceAll("[^A-Z ]", "").trim();
                        if (!keyPart.isEmpty()) {
                            String[] keyParts = keyPart.split("\\s*,\\s*");
                            for (String kp : keyParts) {
                                if (!kp.isEmpty() && pDesignation.equals(kp.trim())) {
                                    return true;
                                }
                            }
                        }
                    }

                    // Personnel's duty_location contains the category name
                    if (!categoryName.isEmpty() && categoryName.length() > 3 && !categoryName.contains(",")) {
                        if (pDutyLocation.contains(categoryName)) {
                            return true;
                        }
                        if (pDutyType.contains(categoryName)) {
                            return true;
                        }
                    }

                    // For long descriptive category names, check if any key word from the 
                    // personnel's duty_type appears in the category name
                    // e.g., duty_type="OFFICE WRITER/COMPUTER OPERATOR" matches category 
                    // "OFFICE WRITER (DCP, ACP, RPI) / COMPUTER OPERATOR"
                    if (categoryName.length() > 15 && !pDutyType.isEmpty() && pDutyType.length() > 3) {
                        if (categoryName.contains(pDutyType)) {
                            return true;
                        }
                        // Check if all significant words from duty_type appear in category name
                        String[] dutyWords = pDutyType.split("[/,\\s]+");
                        boolean allWordsMatch = true;
                        int matchedWords = 0;
                        for (String word : dutyWords) {
                            if (word.length() > 2 && categoryName.contains(word)) {
                                matchedWords++;
                            } else if (word.length() > 2) {
                                allWordsMatch = false;
                            }
                        }
                        if (allWordsMatch && matchedWords >= 2) {
                            return true;
                        }
                    }
                    if (categoryName.length() > 15 && !pDutyLocation.isEmpty() && pDutyLocation.length() > 3) {
                        if (categoryName.contains(pDutyLocation)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());

        // Mark these personnel as assigned so they don't appear in another category
        for (Personnel p : matched) {
            assignedIds.add(p.getId());
        }

        return matched;
    }

    private PersonnelEntry toPersonnelEntry(Personnel p) {
        String displayName;
        if (p.getPersonName() != null && !p.getPersonName().isBlank()) {
            displayName = p.getPersonName();
        } else {
            // Just use the badge ID as-is (it already contains the prefix like "APC-2658")
            displayName = p.getBadgeId() != null ? p.getBadgeId() : "";
        }
        return new PersonnelEntry(displayName, p.getDesignation());
    }

    private void incrementDesignationCount(int[] counts, String designation) {
        if (designation == null) return;
        String normalized = normalizeDesignation(designation);
        int index = DESIGNATION_COLUMNS.indexOf(normalized);
        if (index >= 0) {
            counts[index]++;
        }
    }

    private String normalizeDesignation(String designation) {
        if (designation == null) return "";
        String upper = designation.toUpperCase().trim();
        if (upper.contains("DCP")) return "DCP";
        if (upper.contains("ACP")) return "ACP";
        if (upper.contains("RPI")) return "RPI";
        if (upper.contains("ARSI")) return "ARSI";
        if (upper.contains("RSI")) return "RSI";
        if (upper.contains("AHC")) return "AHC";
        if (upper.contains("APC")) return "APC";
        return upper;
    }
}
