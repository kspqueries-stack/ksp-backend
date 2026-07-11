package com.policescheduler.service;

import com.policescheduler.dto.SectionCreateDto;
import com.policescheduler.dto.SectionUpdateDto;
import com.policescheduler.entity.Section;
import com.policescheduler.exception.ConflictException;
import com.policescheduler.repository.DutyAssignmentRepository;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.SectionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SectionService {

    private static final Logger log = LoggerFactory.getLogger(SectionService.class);

    private final SectionRepository sectionRepository;
    private final DutyTypeRepository dutyTypeRepository;
    private final DutyAssignmentRepository dutyAssignmentRepository;

    public SectionService(SectionRepository sectionRepository,
                          DutyTypeRepository dutyTypeRepository,
                          DutyAssignmentRepository dutyAssignmentRepository) {
        this.sectionRepository = sectionRepository;
        this.dutyTypeRepository = dutyTypeRepository;
        this.dutyAssignmentRepository = dutyAssignmentRepository;
    }

    /**
     * Returns all sections (active + inactive), ordered by sort_order ascending.
     */
    public List<Section> findAll() {
        return sectionRepository.findAll();
    }

    /**
     * Returns only active sections, ordered by sort_order ascending.
     */
    public List<Section> findActive() {
        return sectionRepository.findByIsActiveTrueOrderBySortOrder();
    }

    /**
     * Creates a new section from the given DTO.
     * Throws ConflictException if the code already exists.
     */
    @Transactional
    public Section create(SectionCreateDto dto) {
        if (sectionRepository.existsByCode(dto.code())) {
            throw new ConflictException("Section code already exists: " + dto.code(), 0);
        }

        Section section = new Section();
        section.setCode(dto.code());
        section.setName(dto.name());
        section.setDescription(dto.description());
        section.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        section.setIsActive(dto.isActive() != null ? dto.isActive() : true);

        Section saved = sectionRepository.save(section);
        log.info("Created section: code={}, name={}", saved.getCode(), saved.getName());
        return saved;
    }

    /**
     * Updates an existing section. Code is not updatable.
     * If isActive is set to false, delegates to deactivate() to check for blocking assignments.
     */
    @Transactional
    public Section update(Long id, SectionUpdateDto dto) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section not found with id: " + id));

        // If trying to deactivate, check for blocking assignments
        if (Boolean.FALSE.equals(dto.isActive()) && Boolean.TRUE.equals(section.getIsActive())) {
            long activeAssignments = dutyAssignmentRepository
                    .countActiveAssignmentsBySectionCode(section.getCode());
            if (activeAssignments > 0) {
                throw new ConflictException(
                        "Cannot deactivate section '" + section.getCode()
                                + "': " + activeAssignments + " active duty assignment(s) exist",
                        (int) activeAssignments);
            }
        }

        if (dto.name() != null) {
            section.setName(dto.name());
        }
        if (dto.description() != null) {
            section.setDescription(dto.description());
        }
        if (dto.sortOrder() != null) {
            section.setSortOrder(dto.sortOrder());
        }
        if (dto.isActive() != null) {
            section.setIsActive(dto.isActive());
        }

        Section saved = sectionRepository.save(section);
        log.info("Updated section: id={}, code={}", saved.getId(), saved.getCode());
        return saved;
    }

    /**
     * Deactivates a section by setting is_active to false.
     * Blocks deactivation if active duty_assignments exist for duty types in that section.
     * Throws ConflictException with the count of blocking assignments.
     */
    @Transactional
    public void deactivate(Long id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section not found with id: " + id));

        long activeAssignments = dutyAssignmentRepository
                .countActiveAssignmentsBySectionCode(section.getCode());

        if (activeAssignments > 0) {
            throw new ConflictException(
                    "Cannot deactivate section '" + section.getCode()
                            + "': " + activeAssignments + " active duty assignment(s) exist",
                    (int) activeAssignments);
        }

        section.setIsActive(false);
        sectionRepository.save(section);
        log.info("Deactivated section: id={}, code={}", section.getId(), section.getCode());
    }

    /**
     * Returns the count of duty types belonging to the specified section.
     */
    public int countDutyTypes(String sectionCode) {
        return (int) dutyTypeRepository.countBySection(sectionCode);
    }

    /**
     * Returns the count of active (is_current = true) duty assignments
     * for duty types in the specified section.
     */
    public int countActiveAssignments(String sectionCode) {
        return (int) dutyAssignmentRepository.countActiveAssignmentsBySectionCode(sectionCode);
    }
}
