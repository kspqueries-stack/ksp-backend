package com.policescheduler.controller;

import com.policescheduler.dto.PersonnelDto;
import com.policescheduler.dto.PersonnelFilter;
import com.policescheduler.service.PersonnelService;
import com.policescheduler.repository.SectionRepository;
import com.policescheduler.entity.Section;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedules/v2")
public class ScheduleV2Controller {

    private final PersonnelService personnelService;
    private final SectionRepository sectionRepository;

    public ScheduleV2Controller(PersonnelService personnelService, SectionRepository sectionRepository) {
        this.personnelService = personnelService;
        this.sectionRepository = sectionRepository;
    }

    @GetMapping("/section/{sectionCode}")
    public ResponseEntity<Map<String, Object>> getSectionPersonnel(@PathVariable String sectionCode) {
        var filter = new PersonnelFilter();
        filter.setSection(sectionCode.toUpperCase());
        var results = personnelService.listPersonnel(filter, PageRequest.of(0, 2000));

        // Group by duty_type
        Map<String, List<Map<String, Object>>> grouped = results.getContent().stream()
                .collect(Collectors.groupingBy(
                        p -> p.getDutyType() != null ? p.getDutyType() : "Unassigned",
                        LinkedHashMap::new,
                        Collectors.mapping(p -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", p.getId());
                            m.put("badgeId", p.getBadgeId());
                            m.put("personName", p.getPersonName());
                            m.put("designation", p.getDesignation());
                            m.put("dutyType", p.getDutyType());
                            m.put("location", p.getLocation());
                            m.put("vehicleNumber", p.getVehicleNumber());
                            m.put("isActive", p.getIsActive());
                            m.put("platoonId", p.getPlatoonId());
                            return m;
                        }, Collectors.toList())
                ));

        Section section = sectionRepository.findByCode(sectionCode.toUpperCase()).orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sectionCode", sectionCode.toUpperCase());
        response.put("sectionName", section != null ? section.getName() : "Section " + sectionCode);
        response.put("totalCount", results.getTotalElements());
        response.put("dutyGroups", grouped);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/platoon-chart")
    public ResponseEntity<Map<String, Object>> getPlatoonChart() {
        // Get all personnel from sections C, D, E, F, G (platoon sections)
        List<String> platoonSections = List.of("C", "D", "E", "F", "G");
        Map<String, List<Map<String, Object>>> platoonGroups = new LinkedHashMap<>();

        for (int platoonId = 1; platoonId <= 5; platoonId++) {
            String platoonName = "Platoon " + toRoman(platoonId);
            var filter = new PersonnelFilter();
            // We can't filter by platoon_id via PersonnelFilter, so get all platoon sections
            List<Map<String, Object>> members = new ArrayList<>();
            
            for (String sec : platoonSections) {
                var secFilter = new PersonnelFilter();
                secFilter.setSection(sec);
                var results = personnelService.listPersonnel(secFilter, PageRequest.of(0, 2000));
                final int pid = platoonId;
                results.getContent().stream()
                        .filter(p -> p.getPlatoonId() != null && p.getPlatoonId().intValue() == pid)
                        .forEach(p -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("badgeId", p.getBadgeId());
                            m.put("personName", p.getPersonName());
                            m.put("designation", p.getDesignation());
                            m.put("section", p.getSection());
                            m.put("dutyType", p.getDutyType());
                            members.add(m);
                        });
            }
            platoonGroups.put(platoonName, members);
        }

        // Build the rotation matrix (which platoon does which duty in current period)
        Map<String, String> currentRotation = new LinkedHashMap<>();
        for (var entry : platoonGroups.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                String dutyType = (String) entry.getValue().get(0).get("dutyType");
                currentRotation.put(entry.getKey(), dutyType != null ? dutyType : "Unknown");
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("platoons", platoonGroups);
        response.put("currentRotation", currentRotation);
        response.put("totalPersonnel", platoonGroups.values().stream().mapToInt(List::size).sum());
        return ResponseEntity.ok(response);
    }

    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }
}
