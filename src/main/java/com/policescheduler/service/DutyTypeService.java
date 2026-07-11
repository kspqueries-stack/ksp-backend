package com.policescheduler.service;

import com.policescheduler.dto.DutyTypeCreateDto;
import com.policescheduler.dto.DutyTypeUpdateDto;
import com.policescheduler.entity.DutyType;
import com.policescheduler.exception.ConflictException;
import com.policescheduler.repository.DutyTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DutyTypeService {

    private static final Logger log = LoggerFactory.getLogger(DutyTypeService.class);

    private final DutyTypeRepository dutyTypeRepository;

    public DutyTypeService(DutyTypeRepository dutyTypeRepository) {
        this.dutyTypeRepository = dutyTypeRepository;
    }

    /**
     * Returns duty types optionally filtered by section.
     * If sectionFilter is null or empty, returns all ordered by section + sort_order.
     * Otherwise returns only duty types in the specified section ordered by sort_order.
     */
    public List<DutyType> findAll(String sectionFilter) {
        if (sectionFilter == null || sectionFilter.isBlank()) {
            return dutyTypeRepository.findAllByOrderBySectionAscSortOrderAsc();
        }
        return dutyTypeRepository.findBySectionOrderBySortOrderAsc(sectionFilter);
    }

    /**
     * Creates a new DutyType entity from the given DTO and persists it.
     */
    @Transactional
    public DutyType create(DutyTypeCreateDto dto) {
        DutyType dutyType = new DutyType();
        dutyType.setName(dto.name());
        dutyType.setSection(dto.section());
        dutyType.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : getNextSortOrder(dto.section()));
        dutyType.setLatitude(dto.latitude());
        dutyType.setLongitude(dto.longitude());
        dutyType.setRadiusMeters(dto.radiusMeters());

        DutyType saved = dutyTypeRepository.save(dutyType);
        log.info("Created duty type: id={}, name={}, section={}", saved.getId(), saved.getName(), saved.getSection());
        return saved;
    }

    /**
     * Updates an existing DutyType by applying non-null fields from the DTO.
     * Throws EntityNotFoundException if the duty type does not exist.
     */
    @Transactional
    public DutyType update(Long id, DutyTypeUpdateDto dto) {
        DutyType dutyType = dutyTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Duty type not found with id: " + id));

        if (dto.name() != null) {
            dutyType.setName(dto.name());
        }
        if (dto.section() != null) {
            dutyType.setSection(dto.section());
        }
        if (dto.sortOrder() != null) {
            dutyType.setSortOrder(dto.sortOrder());
        }
        if (dto.latitude() != null) {
            dutyType.setLatitude(dto.latitude());
        }
        if (dto.longitude() != null) {
            dutyType.setLongitude(dto.longitude());
        }
        if (dto.radiusMeters() != null) {
            dutyType.setRadiusMeters(dto.radiusMeters());
        }

        DutyType saved = dutyTypeRepository.save(dutyType);
        log.info("Updated duty type: id={}, name={}, section={}", saved.getId(), saved.getName(), saved.getSection());
        return saved;
    }

    /**
     * Deletes a duty type by ID.
     * Throws EntityNotFoundException if the duty type does not exist.
     * Throws ConflictException if the duty type has active (is_current = true) assignments.
     */
    @Transactional
    public void delete(Long id) {
        DutyType dutyType = dutyTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Duty type not found with id: " + id));

        long activeCount = dutyTypeRepository.countActiveAssignmentsByDutyTypeId(id);
        if (activeCount > 0) {
            throw new ConflictException(
                    "Cannot delete duty type '" + dutyType.getName()
                            + "': " + activeCount + " active assignment(s) exist",
                    (int) activeCount);
        }

        dutyTypeRepository.delete(dutyType);
        log.info("Deleted duty type: id={}, name={}", id, dutyType.getName());
    }

    /**
     * Returns the next available sort_order for the given section.
     * If no duty types exist in the section, returns 1.
     */
    public int getNextSortOrder(String sectionCode) {
        return dutyTypeRepository.findMaxSortOrderBySection(sectionCode)
                .map(max -> max + 1)
                .orElse(1);
    }
}
