package com.policescheduler.service;

import com.policescheduler.dto.*;
import com.policescheduler.entity.*;
import com.policescheduler.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final PlatoonRepository platoonRepository;
    private final PlatoonRotationStateRepository rotationStateRepository;
    private final DutyTypeRepository dutyTypeRepository;
    private final DutyAssignmentRepository dutyAssignmentRepository;
    private final PersonnelRepository personnelRepository;
    private final SectionStrengthRepository sectionStrengthRepository;

    public ScheduleService(PlatoonRepository platoonRepository,
                           PlatoonRotationStateRepository rotationStateRepository,
                           DutyTypeRepository dutyTypeRepository,
                           DutyAssignmentRepository dutyAssignmentRepository,
                           PersonnelRepository personnelRepository,
                           SectionStrengthRepository sectionStrengthRepository) {
        this.platoonRepository = platoonRepository;
        this.rotationStateRepository = rotationStateRepository;
        this.dutyTypeRepository = dutyTypeRepository;
        this.dutyAssignmentRepository = dutyAssignmentRepository;
        this.personnelRepository = personnelRepository;
        this.sectionStrengthRepository = sectionStrengthRepository;
    }

    public SectionOverview getSectionsOverview() {
        return SectionOverview.builder()
                .sectionA(getSectionA())
                .sectionB(getSectionB())
                .sectionC(getSectionC())
                .build();
    }

    public List<DutyAssignmentDto> getSectionA() {
        return mapAssignments(dutyAssignmentRepository.findBySectionAndIsCurrentTrue("A"));
    }

    public List<DutyAssignmentDto> getSectionB() {
        return mapAssignments(dutyAssignmentRepository.findBySectionAndIsCurrentTrue("B"));
    }

    public SectionCData getSectionC() {
        PlatoonRotationDto rotation = getPlatoonRotation();
        List<DutyAssignmentDto> assignments = mapAssignments(
                dutyAssignmentRepository.findBySectionAndIsCurrentTrue("C"));
        return SectionCData.builder()
                .rotation(rotation)
                .assignments(assignments)
                .build();
    }

    public PlatoonRotationDto getPlatoonRotation() {
        PlatoonRotationState state = rotationStateRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Platoon rotation state not found"));

        List<Platoon> platoons = platoonRepository.findAllByOrderByBaseOffsetAsc();
        List<DutyType> sectionCDuties = dutyTypeRepository.findBySectionOrderBySortOrderAsc("C");

        List<PlatoonRotationDto.PlatoonDutyMapping> mappings = platoons.stream()
                .map(p -> {
                    int dutyIndex = (p.getBaseOffset() + state.getCurrentCycleIndex()) % sectionCDuties.size();
                    DutyType duty = sectionCDuties.get(dutyIndex);
                    return PlatoonRotationDto.PlatoonDutyMapping.builder()
                            .platoonId(p.getId())
                            .platoonName(p.getName())
                            .baseOffset(p.getBaseOffset())
                            .currentDutyType(duty.getName())
                            .build();
                })
                .collect(Collectors.toList());

        return PlatoonRotationDto.builder()
                .currentCycleIndex(state.getCurrentCycleIndex())
                .lastRotationDate(state.getLastRotationDate())
                .nextRotationDate(state.getNextRotationDate())
                .platoonMappings(mappings)
                .build();
    }

    @Transactional
    public PlatoonRotationDto rotatePlatoons() {
        PlatoonRotationState state = rotationStateRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Platoon rotation state not found"));

        if (state.getLastRotationDate() != null && state.getLastRotationDate().equals(LocalDate.now())) {
            throw new IllegalStateException("Rotation already performed for current cycle today");
        }

        int newIndex = (state.getCurrentCycleIndex() + 1) % 5;
        state.setCurrentCycleIndex(newIndex);
        state.setLastRotationDate(LocalDate.now());
        state.setNextRotationDate(LocalDate.now().plusDays(15));
        rotationStateRepository.save(state);

        log.info("Platoon rotation advanced to cycle index: {}", newIndex);
        return getPlatoonRotation();
    }

    @Transactional
    public DutyAssignmentDto updateAssignment(Long id, UpdateAssignmentRequest request) {
        DutyAssignment assignment = dutyAssignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Duty assignment not found: " + id));

        if (request.getPersonnelId() != null) assignment.setPersonnelId(request.getPersonnelId());
        if (request.getDutyTypeId() != null) assignment.setDutyTypeId(request.getDutyTypeId());
        if (request.getSection() != null) assignment.setSection(request.getSection());
        if (request.getEffectiveDate() != null) assignment.setEffectiveDate(request.getEffectiveDate());
        if (request.getEndDate() != null) assignment.setEndDate(request.getEndDate());
        if (request.getShift() != null) assignment.setShift(request.getShift());
        if (request.getSubAssignment() != null) assignment.setSubAssignment(request.getSubAssignment());
        if (request.getIsCurrent() != null) assignment.setIsCurrent(request.getIsCurrent());

        DutyAssignment saved = dutyAssignmentRepository.save(assignment);
        return mapAssignment(saved);
    }

    private List<DutyAssignmentDto> mapAssignments(List<DutyAssignment> assignments) {
        Map<Long, Personnel> personnelMap = personnelRepository.findAllById(
                assignments.stream().map(DutyAssignment::getPersonnelId).distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Personnel::getId, p -> p));

        Map<Long, DutyType> dutyTypeMap = dutyTypeRepository.findAllById(
                assignments.stream().map(DutyAssignment::getDutyTypeId).distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(DutyType::getId, d -> d));

        return assignments.stream().map(a -> {
            Personnel p = personnelMap.get(a.getPersonnelId());
            DutyType d = dutyTypeMap.get(a.getDutyTypeId());
            return DutyAssignmentDto.builder()
                    .id(a.getId())
                    .personnelId(a.getPersonnelId())
                    .personnelName(p != null ? p.getPersonName() : null)
                    .badgeId(p != null ? p.getBadgeId() : null)
                    .dutyTypeId(a.getDutyTypeId())
                    .dutyTypeName(d != null ? d.getName() : null)
                    .section(a.getSection())
                    .effectiveDate(a.getEffectiveDate())
                    .endDate(a.getEndDate())
                    .shift(a.getShift())
                    .subAssignment(a.getSubAssignment())
                    .isCurrent(a.getIsCurrent())
                    .build();
        }).collect(Collectors.toList());
    }

    private DutyAssignmentDto mapAssignment(DutyAssignment a) {
        Personnel p = personnelRepository.findById(a.getPersonnelId()).orElse(null);
        DutyType d = dutyTypeRepository.findById(a.getDutyTypeId()).orElse(null);
        return DutyAssignmentDto.builder()
                .id(a.getId())
                .personnelId(a.getPersonnelId())
                .personnelName(p != null ? p.getPersonName() : null)
                .badgeId(p != null ? p.getBadgeId() : null)
                .dutyTypeId(a.getDutyTypeId())
                .dutyTypeName(d != null ? d.getName() : null)
                .section(a.getSection())
                .effectiveDate(a.getEffectiveDate())
                .endDate(a.getEndDate())
                .shift(a.getShift())
                .subAssignment(a.getSubAssignment())
                .isCurrent(a.getIsCurrent())
                .build();
    }
}
