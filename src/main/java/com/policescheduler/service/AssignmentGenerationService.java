package com.policescheduler.service;

import com.policescheduler.entity.CycleDutyAssignment;
import com.policescheduler.entity.CyclePlatoonSection;
import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.Section;
import com.policescheduler.entity.ShiftGroup;
import com.policescheduler.repository.CycleDutyAssignmentRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.SectionRepository;
import com.policescheduler.repository.ShiftGroupRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AssignmentGenerationService {

    private final PersonnelRepository personnelRepository;
    private final CycleDutyAssignmentRepository cycleDutyAssignmentRepository;
    private final ShiftGroupRepository shiftGroupRepository;
    private final SectionRepository sectionRepository;

    // Rotation pattern: no same shift on consecutive days
    // dayIndex % 3 == 0 → A=DAY,       B=AFTERNOON, C=NIGHT
    // dayIndex % 3 == 1 → A=NIGHT,     B=DAY,       C=AFTERNOON
    // dayIndex % 3 == 2 → A=AFTERNOON, B=NIGHT,     C=DAY
    private static final String[][] ROTATION = {
        {"DAY", "AFTERNOON", "NIGHT"},      // day % 3 == 0
        {"NIGHT", "DAY", "AFTERNOON"},      // day % 3 == 1
        {"AFTERNOON", "NIGHT", "DAY"}       // day % 3 == 2
    };

    public AssignmentGenerationService(PersonnelRepository personnelRepository,
                                       CycleDutyAssignmentRepository cycleDutyAssignmentRepository,
                                       ShiftGroupRepository shiftGroupRepository,
                                       SectionRepository sectionRepository) {
        this.personnelRepository = personnelRepository;
        this.cycleDutyAssignmentRepository = cycleDutyAssignmentRepository;
        this.shiftGroupRepository = shiftGroupRepository;
        this.sectionRepository = sectionRepository;
    }

    /**
     * Generates duty assignments for a cycle with 3-shift rotation.
     * Personnel are divided into groups A, B, C and rotate through DAY/AFTERNOON/NIGHT shifts.
     */
    public List<CycleDutyAssignment> generateAssignments(Long cycleId, LocalDate startDate,
                                                          LocalDate endDate,
                                                          List<CyclePlatoonSection> mappings) {
        List<CycleDutyAssignment> assignments = new ArrayList<>();

        for (CyclePlatoonSection mapping : mappings) {
            // Get active personnel for this platoon, sorted by ID for deterministic grouping
            List<Personnel> activePersonnel = personnelRepository
                    .findByPlatoonIdAndIsActiveTrue(mapping.getPlatoonId());
            activePersonnel.sort(Comparator.comparingLong(Personnel::getId));

            if (activePersonnel.isEmpty()) {
                continue;
            }

            // Get section name for duty_name
            String dutyName = sectionRepository.findById(mapping.getSectionId())
                    .map(Section::getName)
                    .orElse(null);

            // Divide into 3 groups
            int totalSize = activePersonnel.size();
            int groupSize = totalSize / 3;
            int remainder = totalSize % 3;

            // Group A gets extra if remainder >= 1, Group B gets extra if remainder == 2
            int groupASize = groupSize + (remainder >= 1 ? 1 : 0);
            int groupBSize = groupSize + (remainder >= 2 ? 1 : 0);

            List<Personnel> groupA = activePersonnel.subList(0, groupASize);
            List<Personnel> groupB = activePersonnel.subList(groupASize, groupASize + groupBSize);
            List<Personnel> groupC = activePersonnel.subList(groupASize + groupBSize, totalSize);

            // Save shift groups to database
            saveShiftGroup(cycleId, mapping.getSectionId(), mapping.getPlatoonId(), "A", groupA);
            saveShiftGroup(cycleId, mapping.getSectionId(), mapping.getPlatoonId(), "B", groupB);
            saveShiftGroup(cycleId, mapping.getSectionId(), mapping.getPlatoonId(), "C", groupC);

            // Generate assignments for each day
            long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            for (int dayIndex = 0; dayIndex < totalDays; dayIndex++) {
                LocalDate date = startDate.plusDays(dayIndex);
                int pattern = dayIndex % 3;

                String groupAShift = ROTATION[pattern][0];
                String groupBShift = ROTATION[pattern][1];
                String groupCShift = ROTATION[pattern][2];

                // Create assignments for Group A
                for (Personnel person : groupA) {
                    assignments.add(createAssignment(cycleId, date, mapping, person, groupAShift, "A", dutyName));
                }
                // Create assignments for Group B
                for (Personnel person : groupB) {
                    assignments.add(createAssignment(cycleId, date, mapping, person, groupBShift, "B", dutyName));
                }
                // Create assignments for Group C
                for (Personnel person : groupC) {
                    assignments.add(createAssignment(cycleId, date, mapping, person, groupCShift, "C", dutyName));
                }
            }
        }

        return cycleDutyAssignmentRepository.saveAll(assignments);
    }

    private CycleDutyAssignment createAssignment(Long cycleId, LocalDate date,
                                                   CyclePlatoonSection mapping,
                                                   Personnel person, String shiftType,
                                                   String groupIndex, String dutyName) {
        CycleDutyAssignment assignment = new CycleDutyAssignment();
        assignment.setCycleId(cycleId);
        assignment.setDate(date);
        assignment.setPlatoonId(mapping.getPlatoonId());
        assignment.setSectionId(mapping.getSectionId());
        assignment.setPersonId(person.getId());
        assignment.setShiftType(shiftType);
        assignment.setIsOverride(false);
        assignment.setGroupIndex(groupIndex);
        assignment.setStatus("ACTIVE");
        assignment.setDutyName(dutyName);
        return assignment;
    }

    private void saveShiftGroup(Long cycleId, Long sectionId, Long platoonId,
                                 String groupName, List<Personnel> personnel) {
        ShiftGroup group = new ShiftGroup();
        group.setCycleId(cycleId);
        group.setSectionId(sectionId);
        group.setPlatoonId(platoonId);
        group.setGroupName(groupName);
        // Store personnel IDs as JSON array
        String ids = personnel.stream()
                .map(p -> String.valueOf(p.getId()))
                .collect(Collectors.joining(",", "[", "]"));
        group.setPersonnelIds(ids);
        shiftGroupRepository.save(group);
    }
}
