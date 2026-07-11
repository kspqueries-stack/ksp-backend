package com.policescheduler.service;

import com.policescheduler.dto.ReassignmentRequestDto;
import com.policescheduler.dto.ReassignmentResultDto;
import com.policescheduler.entity.DutyType;
import com.policescheduler.entity.Section;
import com.policescheduler.repository.DutyAssignmentRepository;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.SectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for bulk reassignment of duty types between sections.
 * Handles transactional updates across duty_types, duty_assignments, and personnel tables.
 * All operations execute within a single transaction — if any step fails, all changes roll back.
 */
@Service
public class ReassignmentService {

    private static final Logger log = LoggerFactory.getLogger(ReassignmentService.class);

    private final SectionRepository sectionRepository;
    private final DutyTypeRepository dutyTypeRepository;
    private final DutyAssignmentRepository dutyAssignmentRepository;
    private final PersonnelRepository personnelRepository;

    public ReassignmentService(SectionRepository sectionRepository,
                               DutyTypeRepository dutyTypeRepository,
                               DutyAssignmentRepository dutyAssignmentRepository,
                               PersonnelRepository personnelRepository) {
        this.sectionRepository = sectionRepository;
        this.dutyTypeRepository = dutyTypeRepository;
        this.dutyAssignmentRepository = dutyAssignmentRepository;
        this.personnelRepository = personnelRepository;
    }

    /**
     * Reassigns the specified duty types to the target section.
     * Updates duty_types.section, duty_assignments.section (where is_current = true),
     * and personnel.section for affected duty types — all within a single transaction.
     *
     * @param request the reassignment request containing duty type IDs and target section
     * @return result DTO with counts of updated records
     * @throws IllegalArgumentException if target section does not exist or is inactive
     */
    @Transactional
    public ReassignmentResultDto reassign(ReassignmentRequestDto request) {
        LocalDateTime timestamp = LocalDateTime.now();
        List<Long> dutyTypeIds = request.dutyTypeIds();
        String targetSectionCode = request.targetSection();

        log.info("[{}] Reassignment requested: {} duty types to section '{}'",
                timestamp, dutyTypeIds.size(), targetSectionCode);

        // Step 1: Validate target section exists and is active
        Section targetSection = sectionRepository.findByCode(targetSectionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Target section '" + targetSectionCode + "' does not exist"));

        if (!Boolean.TRUE.equals(targetSection.getIsActive())) {
            throw new IllegalArgumentException(
                    "Target section '" + targetSectionCode + "' is not active");
        }

        // Step 2: Look up all duty types by the provided IDs
        List<DutyType> dutyTypes = dutyTypeRepository.findAllById(dutyTypeIds);
        if (dutyTypes.isEmpty()) {
            log.warn("[{}] No duty types found for provided IDs: {}", timestamp, dutyTypeIds);
            return new ReassignmentResultDto(0, 0, 0, "UNKNOWN", targetSectionCode);
        }

        // Step 3: Determine the source section(s) from the duty types
        String sourceSection = dutyTypes.stream()
                .map(DutyType::getSection)
                .distinct()
                .collect(Collectors.joining(","));

        // Step 4: Update duty_types.section to the target section for all specified IDs
        int dutyTypesUpdated = dutyTypeRepository.bulkUpdateSection(dutyTypeIds, targetSectionCode);

        // Step 5: Update duty_assignments.section where is_current = true and duty_type_id in list
        int dutyAssignmentsUpdated = dutyAssignmentRepository.bulkUpdateSectionByDutyTypeIds(
                dutyTypeIds, targetSectionCode);

        // Step 6: Update personnel.section where duty_type_id matches the reassigned duty types
        int personnelUpdated = personnelRepository.bulkUpdateSectionByDutyTypeIds(
                dutyTypeIds, targetSectionCode);

        // Step 7: Log the audit trail with timestamp, source/target sections, and counts
        log.info("[{}] Reassignment completed: source='{}', target='{}', " +
                        "dutyTypesUpdated={}, dutyAssignmentsUpdated={}, personnelUpdated={}",
                timestamp, sourceSection, targetSectionCode,
                dutyTypesUpdated, dutyAssignmentsUpdated, personnelUpdated);

        // Step 8: Return ReassignmentResultDto with counts
        return new ReassignmentResultDto(
                dutyTypesUpdated,
                dutyAssignmentsUpdated,
                personnelUpdated,
                sourceSection,
                targetSectionCode
        );
    }
}
