package com.policescheduler.schedule;

import com.policescheduler.entity.DutyAssignment;
import com.policescheduler.entity.DutyType;
import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.Section;
import com.policescheduler.repository.DutyAssignmentRepository;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.SectionRepository;
import com.policescheduler.schedule.dto.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedules/v3")
public class ScheduleV3Controller {

    private final PersonnelRepository personnelRepository;
    private final SectionRepository sectionRepository;
    private final DutyAssignmentRepository dutyAssignmentRepository;
    private final DutyTypeRepository dutyTypeRepository;
    private final AtomicLong shiftIdGenerator = new AtomicLong(1000);

    private static final List<ShiftTypeDto> SHIFT_TYPES = List.of(
            new ShiftTypeDto("morning", "Morning", "#3B82F6", "06:00", "14:00"),
            new ShiftTypeDto("afternoon", "Afternoon", "#10B981", "14:00", "22:00"),
            new ShiftTypeDto("night", "Night", "#F97316", "22:00", "06:00"),
            new ShiftTypeDto("special", "Special", "#8B5CF6", "08:00", "20:00")
    );

    public ScheduleV3Controller(PersonnelRepository personnelRepository,
                                 SectionRepository sectionRepository,
                                 DutyAssignmentRepository dutyAssignmentRepository,
                                 DutyTypeRepository dutyTypeRepository) {
        this.personnelRepository = personnelRepository;
        this.sectionRepository = sectionRepository;
        this.dutyAssignmentRepository = dutyAssignmentRepository;
        this.dutyTypeRepository = dutyTypeRepository;
    }

    /**
     * Builds a lookup map from section code (e.g., "A") to section full name
     * (e.g., "Section A - Static Duties") so that the grid's group field matches
     * the values returned by the /groups endpoint.
     */
    private Map<String, String> buildSectionCodeToNameMap() {
        List<Section> sections = sectionRepository.findByIsActiveTrueOrderBySortOrder();
        if (sections == null || sections.isEmpty()) {
            return Collections.emptyMap();
        }
        return sections.stream()
                .collect(Collectors.toMap(Section::getCode, Section::getName, (a, b) -> a));
    }

    /**
     * GET /api/schedules/v3/grid?start={date}&end={date}
     * Returns TeamMemberSchedule[] with shifts for the given date range.
     */
    @GetMapping("/grid")
    public ResponseEntity<List<TeamMemberScheduleDto>> getGrid(
            @RequestParam String start,
            @RequestParam String end) {

        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);

        // Try to pull real personnel from the database
        List<Personnel> personnel = personnelRepository.findByIsActiveTrue();

        // Build section code → name map so group field matches /groups endpoint
        Map<String, String> sectionMap = buildSectionCodeToNameMap();

        List<TeamMemberScheduleDto> schedules;

        if (personnel != null && !personnel.isEmpty()) {
            // Use real personnel data — return all active members (frontend handles pagination)
            schedules = personnel.stream()
                    .map(p -> buildTeamMemberSchedule(p, startDate, endDate, sectionMap))
                    .collect(Collectors.toList());
        } else {
            // Fallback to hardcoded sample data
            schedules = buildSampleSchedules(startDate, endDate, sectionMap);
        }

        return ResponseEntity.ok(schedules);
    }

    /**
     * GET /api/schedules/v3/open-shifts?start={date}&end={date}
     * Returns OpenShift[] for unassigned shifts in the date range.
     */
    @GetMapping("/open-shifts")
    public ResponseEntity<List<OpenShiftDto>> getOpenShifts(
            @RequestParam String start,
            @RequestParam String end) {

        LocalDate startDate = LocalDate.parse(start);

        List<OpenShiftDto> openShifts = List.of(
                new OpenShiftDto(
                        shiftIdGenerator.incrementAndGet(),
                        "morning", "Morning", "#3B82F6",
                        startDate.plusDays(1), "06:00", "14:00",
                        "Main Gate", "Extra coverage needed", 2
                ),
                new OpenShiftDto(
                        shiftIdGenerator.incrementAndGet(),
                        "night", "Night", "#F97316",
                        startDate.plusDays(3), "22:00", "06:00",
                        "Control Room", "Weekend night shift", 1
                ),
                new OpenShiftDto(
                        shiftIdGenerator.incrementAndGet(),
                        "special", "Special", "#8B5CF6",
                        startDate.plusDays(5), "08:00", "20:00",
                        "Event Venue", "VIP event security", 3
                )
        );

        return ResponseEntity.ok(openShifts);
    }

    /**
     * GET /api/schedules/v3/shift-types
     * Returns the available shift types.
     */
    @GetMapping("/shift-types")
    public ResponseEntity<List<ShiftTypeDto>> getShiftTypes() {
        return ResponseEntity.ok(SHIFT_TYPES);
    }

    /**
     * GET /api/schedules/v3/groups
     * Returns distinct section names (groups) from the database.
     */
    @GetMapping("/groups")
    public ResponseEntity<List<String>> getGroups() {
        List<Section> sections = sectionRepository.findByIsActiveTrueOrderBySortOrder();

        if (sections != null && !sections.isEmpty()) {
            List<String> groups = sections.stream()
                    .map(s -> s.getName())
                    .collect(Collectors.toList());
            return ResponseEntity.ok(groups);
        }

        // Fallback
        return ResponseEntity.ok(List.of("Section A", "Section B", "Section C"));
    }

    /**
     * POST /api/schedules/v3/shifts
     * Creates a new shift assignment. Returns the created shift.
     */
    @PostMapping("/shifts")
    public ResponseEntity<ShiftDto> createShift(@RequestBody CreateShiftRequest request) {
        // Resolve shift type info
        ShiftTypeDto shiftType = SHIFT_TYPES.stream()
                .filter(st -> st.getId().equals(request.getShiftTypeId()))
                .findFirst()
                .orElse(SHIFT_TYPES.get(0));

        ShiftDto created = new ShiftDto(
                shiftIdGenerator.incrementAndGet(),
                shiftType.getId(),
                shiftType.getName(),
                shiftType.getColor(),
                request.getDate(),
                request.getStartTime() != null ? request.getStartTime() : shiftType.getStartTime(),
                request.getEndTime() != null ? request.getEndTime() : shiftType.getEndTime(),
                request.getNotes()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * POST /api/schedules/v3/publish
     * Publishes the schedule for the given date range.
     */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publishSchedule(@RequestBody(required = false) PublishRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "published");
        response.put("message", "Schedule published successfully");
        response.put("publishedAt", java.time.LocalDateTime.now().toString());
        if (request != null && request.getStartDate() != null) {
            response.put("startDate", request.getStartDate().toString());
            response.put("endDate", request.getEndDate() != null ? request.getEndDate().toString() : null);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/schedules/v3/export?start={date}&end={date}&format={csv|pdf}
     * Exports the schedule in the specified format.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportSchedule(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(defaultValue = "csv") String format) {

        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);

        if ("pdf".equalsIgnoreCase(format)) {
            // Stub: return a simple text file pretending to be PDF
            String content = "PDF export placeholder for " + startDate + " to " + endDate;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "schedule_" + start + "_" + end + ".pdf");
            return new ResponseEntity<>(content.getBytes(), headers, HttpStatus.OK);
        }

        // Default: CSV export
        StringBuilder csv = new StringBuilder();
        csv.append("Name,Badge ID,Section,Date,Shift Type,Start Time,End Time\n");

        List<Personnel> personnel = personnelRepository.findByIsActiveTrue();
        List<Personnel> sample = (personnel != null && !personnel.isEmpty())
                ? personnel
                : Collections.emptyList();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            for (Personnel p : sample) {
                ShiftTypeDto shift = SHIFT_TYPES.get(Math.abs(p.getPersonName().hashCode()) % SHIFT_TYPES.size());
                csv.append(String.format("%s,%s,%s,%s,%s,%s,%s\n",
                        p.getPersonName(),
                        p.getBadgeId(),
                        p.getSection(),
                        current.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        shift.getName(),
                        shift.getStartTime(),
                        shift.getEndTime()));
            }
            current = current.plusDays(1);
        }

        // If no personnel, provide sample data
        if (sample.isEmpty()) {
            current = startDate;
            while (!current.isAfter(endDate)) {
                csv.append(String.format("John Doe,KP001,A,%s,Morning,06:00,14:00\n", current));
                csv.append(String.format("Jane Smith,KP002,B,%s,Afternoon,14:00,22:00\n", current));
                current = current.plusDays(1);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment",
                "schedule_" + start + "_" + end + ".csv");

        return new ResponseEntity<>(csv.toString().getBytes(), headers, HttpStatus.OK);
    }

    // --- Private helper methods ---

    private TeamMemberScheduleDto buildTeamMemberSchedule(Personnel p, LocalDate startDate, LocalDate endDate, Map<String, String> sectionMap) {
        List<ShiftDto> shifts = new ArrayList<>();
        LocalDate current = startDate;
        int dayIndex = 0;

        // Look up the person's current duty assignment for a descriptive shift label
        String dutyTypeName = null;
        Optional<DutyAssignment> assignment = dutyAssignmentRepository.findByPersonnelIdAndIsCurrentTrue(p.getId());
        if (assignment.isPresent()) {
            Optional<DutyType> dutyType = dutyTypeRepository.findById(assignment.get().getDutyTypeId());
            if (dutyType.isPresent()) {
                dutyTypeName = dutyType.get().getName();
            }
        }

        while (!current.isAfter(endDate)) {
            // Assign shifts based on a pattern derived from the person's name hash
            int shiftIndex = (Math.abs(p.getPersonName().hashCode()) + dayIndex) % (SHIFT_TYPES.size() + 1);
            if (shiftIndex < SHIFT_TYPES.size()) {
                // Has a shift on this day
                ShiftTypeDto shiftType = SHIFT_TYPES.get(shiftIndex);
                // Use duty type name as label if available, otherwise fall back to generic shift name
                String shiftLabel = dutyTypeName != null ? dutyTypeName : shiftType.getName();
                shifts.add(new ShiftDto(
                        shiftIdGenerator.incrementAndGet(),
                        shiftType.getId(),
                        shiftLabel,
                        shiftType.getColor(),
                        current,
                        shiftType.getStartTime(),
                        shiftType.getEndTime(),
                        null
                ));
            }
            // else: day off (no shift)
            current = current.plusDays(1);
            dayIndex++;
        }

        // Resolve the section code to full name for the group field
        String sectionCode = p.getSection();
        String groupName = sectionMap.getOrDefault(sectionCode, sectionCode);

        return new TeamMemberScheduleDto(
                p.getId(),
                p.getBadgeId(),
                p.getPersonName(),
                p.getDesignation(),
                sectionCode,
                groupName,
                shifts
        );
    }

    private List<TeamMemberScheduleDto> buildSampleSchedules(LocalDate startDate, LocalDate endDate, Map<String, String> sectionMap) {
        String[][] sampleData = {
                {"1", "KP001", "John Doe", "Constable", "A"},
                {"2", "KP002", "Jane Smith", "Head Constable", "A"},
                {"3", "KP003", "Raj Kumar", "SI", "B"},
                {"4", "KP004", "Priya Sharma", "Constable", "B"},
                {"5", "KP005", "Anil Patel", "ASI", "C"},
                {"6", "KP006", "Meera Nair", "Constable", "C"}
        };

        List<TeamMemberScheduleDto> schedules = new ArrayList<>();
        for (String[] data : sampleData) {
            List<ShiftDto> shifts = new ArrayList<>();
            LocalDate current = startDate;
            int dayIndex = 0;

            while (!current.isAfter(endDate)) {
                int shiftIndex = (Math.abs(data[2].hashCode()) + dayIndex) % (SHIFT_TYPES.size() + 1);
                if (shiftIndex < SHIFT_TYPES.size()) {
                    ShiftTypeDto shiftType = SHIFT_TYPES.get(shiftIndex);
                    shifts.add(new ShiftDto(
                            shiftIdGenerator.incrementAndGet(),
                            shiftType.getId(),
                            shiftType.getName(),
                            shiftType.getColor(),
                            current,
                            shiftType.getStartTime(),
                            shiftType.getEndTime(),
                            null
                    ));
                }
                current = current.plusDays(1);
                dayIndex++;
            }

            String sectionCode = data[4];
            String groupName = sectionMap.getOrDefault(sectionCode, "Section " + sectionCode);

            schedules.add(new TeamMemberScheduleDto(
                    Long.parseLong(data[0]),
                    data[1],
                    data[2],
                    data[3],
                    sectionCode,
                    groupName,
                    shifts
            ));
        }
        return schedules;
    }
}
