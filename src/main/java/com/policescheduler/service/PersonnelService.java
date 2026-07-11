package com.policescheduler.service;

import com.policescheduler.dto.*;
import com.policescheduler.entity.Personnel;
import com.policescheduler.repository.PersonnelRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PersonnelService {

    private static final Logger log = LoggerFactory.getLogger(PersonnelService.class);

    private final PersonnelRepository personnelRepository;

    public PersonnelService(PersonnelRepository personnelRepository) {
        this.personnelRepository = personnelRepository;
    }

    public Page<PersonnelDto> listPersonnel(PersonnelFilter filter, Pageable pageable) {
        Specification<Personnel> spec = buildSpecification(filter);
        return personnelRepository.findAll(spec, pageable).map(this::toDto);
    }

    public PersonnelDto getByBadgeId(String badgeId) {
        Personnel personnel = personnelRepository.findByBadgeId(badgeId)
                .orElseThrow(() -> new EntityNotFoundException("Personnel record not found for badge ID: " + badgeId));
        return toDto(personnel);
    }

    @Transactional
    public PersonnelDto create(CreatePersonnelRequest request) {
        if (personnelRepository.existsByBadgeId(request.getBadgeId())) {
            throw new IllegalStateException("Badge ID already exists: " + request.getBadgeId());
        }
        validateDriverFields(request.getDutyType(), request.getVehicleNumber(),
                request.getDutyLocation(), request.getPdms(), request.getLicenceType());

        Personnel personnel = new Personnel();
        personnel.setBadgeId(request.getBadgeId());
        personnel.setPersonName(request.getPersonName());
        personnel.setDutyType(request.getDutyType());
        personnel.setLocation(request.getLocation());
        personnel.setPhoneNumber(request.getPhoneNumber());
        personnel.setEmail(request.getEmail());
        personnel.setDesignation(request.getDesignation());
        personnel.setSection(request.getSection());
        personnel.setDateOfJoining(request.getDateOfJoining());
        personnel.setVehicleNumber(request.getVehicleNumber());
        personnel.setDutyLocation(request.getDutyLocation());
        personnel.setPdms(request.getPdms());
        personnel.setLicenceType(request.getLicenceType());
        personnel.setDeployedFrom(request.getDeployedFrom());
        personnel.setPlatoonId(request.getPlatoonId());
        personnel.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Personnel saved = personnelRepository.save(personnel);
        log.info("Created personnel record with badge ID: {}", saved.getBadgeId());
        return toDto(saved);
    }

    @Transactional
    public PersonnelDto update(String badgeId, UpdatePersonnelRequest request) {
        Personnel personnel = personnelRepository.findByBadgeId(badgeId)
                .orElseThrow(() -> new EntityNotFoundException("Personnel record not found for badge ID: " + badgeId));

        String dutyType = request.getDutyType() != null ? request.getDutyType() : personnel.getDutyType();
        String vehicleNumber = request.getVehicleNumber() != null ? request.getVehicleNumber() : personnel.getVehicleNumber();
        String dutyLocation = request.getDutyLocation() != null ? request.getDutyLocation() : personnel.getDutyLocation();
        String pdms = request.getPdms() != null ? request.getPdms() : personnel.getPdms();
        String licenceType = request.getLicenceType() != null ? request.getLicenceType() : personnel.getLicenceType();
        validateDriverFields(dutyType, vehicleNumber, dutyLocation, pdms, licenceType);

        if (request.getPersonName() != null) personnel.setPersonName(request.getPersonName());
        if (request.getDutyType() != null) personnel.setDutyType(request.getDutyType());
        if (request.getLocation() != null) personnel.setLocation(request.getLocation());
        if (request.getPhoneNumber() != null) personnel.setPhoneNumber(request.getPhoneNumber());
        if (request.getEmail() != null) personnel.setEmail(request.getEmail());
        if (request.getDesignation() != null) personnel.setDesignation(request.getDesignation());
        if (request.getSection() != null) personnel.setSection(request.getSection());
        if (request.getDateOfJoining() != null) personnel.setDateOfJoining(request.getDateOfJoining());
        if (request.getVehicleNumber() != null) personnel.setVehicleNumber(request.getVehicleNumber());
        if (request.getDutyLocation() != null) personnel.setDutyLocation(request.getDutyLocation());
        if (request.getPdms() != null) personnel.setPdms(request.getPdms());
        if (request.getLicenceType() != null) personnel.setLicenceType(request.getLicenceType());
        if (request.getDeployedFrom() != null) personnel.setDeployedFrom(request.getDeployedFrom());
        if (request.getPlatoonId() != null) personnel.setPlatoonId(request.getPlatoonId());
        if (request.getIsActive() != null) personnel.setIsActive(request.getIsActive());

        Personnel saved = personnelRepository.save(personnel);
        log.info("Updated personnel record with badge ID: {}", saved.getBadgeId());
        return toDto(saved);
    }

    @Transactional
    public void delete(String badgeId) {
        Personnel personnel = personnelRepository.findByBadgeId(badgeId)
                .orElseThrow(() -> new EntityNotFoundException("Personnel record not found for badge ID: " + badgeId));
        personnelRepository.delete(personnel);
        log.info("Deleted personnel record with badge ID: {}", badgeId);
    }

    private void validateDriverFields(String dutyType, String vehicleNumber,
                                       String dutyLocation, String pdms, String licenceType) {
        if ("DRIVER".equalsIgnoreCase(dutyType)) {
            List<String> missing = new ArrayList<>();
            if (vehicleNumber == null || vehicleNumber.isBlank()) missing.add("vehicleNumber");
            if (dutyLocation == null || dutyLocation.isBlank()) missing.add("dutyLocation");
            if (pdms == null || pdms.isBlank()) missing.add("pdms");
            if (licenceType == null || licenceType.isBlank()) missing.add("licenceType");
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Driver fields required when duty type is DRIVER: " + String.join(", ", missing));
            }
        }
    }

    private Specification<Personnel> buildSpecification(PersonnelFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String search = "%" + filter.getSearch().toLowerCase() + "%";
                Predicate searchPredicate = cb.or(
                        cb.like(cb.lower(root.get("badgeId")), search),
                        cb.like(cb.lower(root.get("personName")), search),
                        cb.like(cb.lower(cb.coalesce(root.get("dutyType"), "")), search),
                        cb.like(cb.lower(cb.coalesce(root.get("location"), "")), search),
                        cb.like(cb.lower(cb.coalesce(root.get("phoneNumber"), "")), search),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), search),
                        cb.like(cb.lower(cb.coalesce(root.get("designation"), "")), search),
                        cb.like(cb.lower(root.get("section")), search)
                );
                predicates.add(searchPredicate);
            }
            if (filter.getSection() != null && !filter.getSection().isBlank()) {
                predicates.add(cb.equal(root.get("section"), filter.getSection()));
            }
            if (filter.getDutyType() != null && !filter.getDutyType().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("dutyType")), filter.getDutyType().toLowerCase()));
            }
            if (filter.getDesignation() != null && !filter.getDesignation().isBlank()) {
                predicates.add(cb.equal(root.get("designation"), filter.getDesignation()));
            }
            if (filter.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), filter.getIsActive()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PersonnelDto toDto(Personnel p) {
        return PersonnelDto.builder()
                .id(p.getId())
                .badgeId(p.getBadgeId())
                .personName(p.getPersonName())
                .dutyType(p.getDutyType())
                .location(p.getLocation())
                .phoneNumber(p.getPhoneNumber())
                .email(p.getEmail())
                .designation(p.getDesignation())
                .section(p.getSection())
                .dateOfJoining(p.getDateOfJoining())
                .vehicleNumber(p.getVehicleNumber())
                .dutyLocation(p.getDutyLocation())
                .pdms(p.getPdms())
                .licenceType(p.getLicenceType())
                .deployedFrom(p.getDeployedFrom())
                .platoonId(p.getPlatoonId())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
