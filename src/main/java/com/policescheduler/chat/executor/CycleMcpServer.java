package com.policescheduler.chat.executor;

import com.policescheduler.chat.model.ProcessingContext;
import com.policescheduler.dto.*;
import com.policescheduler.dto.chat.ConfirmationResponseData;
import com.policescheduler.dto.chat.FormResponseData;
import com.policescheduler.dto.chat.TableResponseData;
import com.policescheduler.entity.CycleDutyAssignment;
import com.policescheduler.entity.Personnel;
import com.policescheduler.repository.CycleDutyAssignmentRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.PlatoonRepository;
import com.policescheduler.repository.SectionRepository;
import com.policescheduler.service.CycleAuditService;
import com.policescheduler.service.CycleService;
import com.policescheduler.service.LeaveConflictDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP server providing chat tools for platoon cycle management.
 * Registered automatically via Spring DI into McpHost.
 */
@Service
public class CycleMcpServer implements McpServer {

    private static final Logger log = LoggerFactory.getLogger(CycleMcpServer.class);

    private final CycleService cycleService;
    private final CycleDutyAssignmentRepository cycleDutyAssignmentRepository;
    private final LeaveConflictDetector leaveConflictDetector;
    private final PersonnelRepository personnelRepository;
    private final SectionRepository sectionRepository;
    private final PlatoonRepository platoonRepository;
    private final CycleAuditService cycleAuditService;

    public CycleMcpServer(CycleService cycleService,
                          CycleDutyAssignmentRepository cycleDutyAssignmentRepository,
                          LeaveConflictDetector leaveConflictDetector,
                          PersonnelRepository personnelRepository,
                          SectionRepository sectionRepository,
                          PlatoonRepository platoonRepository,
                          CycleAuditService cycleAuditService) {
        this.cycleService = cycleService;
        this.cycleDutyAssignmentRepository = cycleDutyAssignmentRepository;
        this.leaveConflictDetector = leaveConflictDetector;
        this.personnelRepository = personnelRepository;
        this.sectionRepository = sectionRepository;
        this.platoonRepository = platoonRepository;
        this.cycleAuditService = cycleAuditService;
    }

    @Override
    public String getServerId() {
        return "cycle-management";
    }

    @Override
    public List<McpToolDefinition> listTools() {
        return List.of(
            new McpToolDefinition("create_cycle",
                "Create a new platoon rotation cycle with platoon-section mappings and auto-generated duty assignments",
                Map.of()),
            new McpToolDefinition("list_cycles",
                "List all platoon rotation cycles, optionally filtered by status (ACTIVE, COMPLETED, DELETED)",
                Map.of()),
            new McpToolDefinition("get_cycle_details",
                "Get detailed information about a specific cycle including platoon-section mappings",
                Map.of()),
            new McpToolDefinition("update_cycle",
                "Update an existing cycle's fields (start_date, rotation_days, platoon_sections, status)",
                Map.of()),
            new McpToolDefinition("delete_cycle",
                "Soft-delete a cycle by setting its status to DELETED",
                Map.of()),
            new McpToolDefinition("reassign_duty",
                "Reassign a duty assignment to a different person, validating leave conflicts",
                Map.of()),
            new McpToolDefinition("auto_reassign_duty",
                "Auto-reassign a duty to a randomly available person from another platoon",
                Map.of()),
            new McpToolDefinition("get_cycle_activities",
                "Get audit log / activity history showing all cycle changes",
                Map.of())
        );
    }

    @Override
    public McpToolResult executeTool(String toolName, Map<String, Object> parameters, ProcessingContext context) {
        log.info("CycleMcpServer executing tool: {} with params: {}", toolName, parameters);
        try {
            Object result = switch (toolName) {
                case "create_cycle" -> executeCreateCycle(parameters);
                case "list_cycles" -> executeListCycles(parameters);
                case "get_cycle_details" -> executeGetCycleDetails(parameters);
                case "update_cycle" -> executeUpdateCycle(parameters);
                case "delete_cycle" -> executeDeleteCycle(parameters);
                case "reassign_duty" -> executeReassignDuty(parameters);
                case "auto_reassign_duty" -> executeAutoReassignDuty(parameters);
                case "get_cycle_activities" -> executeGetCycleActivities(parameters);
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };
            return McpToolResult.success(result);
        } catch (Exception e) {
            log.error("Tool execution error for {}: {}", toolName, e.getMessage(), e);
            return McpToolResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }

    /**
     * Creates a cycle. If parameters are complete, calls CycleService directly.
     * If parameters are incomplete, auto-generates platoon-section mappings and returns a pre-filled form.
     */
    @SuppressWarnings("unchecked")
    private Object executeCreateCycle(Map<String, Object> params) {
        String startDateStr = (String) params.get("start_date");
        Object rotationDaysObj = params.get("rotation_days");
        Object platoonSectionsObj = params.get("platoon_sections");

        // If all parameters are provided, create the cycle directly
        if (startDateStr != null && rotationDaysObj != null && platoonSectionsObj != null) {
            LocalDate startDate = LocalDate.parse(startDateStr);
            Integer rotationDays = toInteger(rotationDaysObj);

            List<PlatoonSectionMapping> mappings = parsePlatoonSections(platoonSectionsObj);

            CycleCreateRequest request = new CycleCreateRequest(startDate, rotationDays, mappings);
            CycleResponse response = cycleService.createCycle(request);

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Cycle ID", String.valueOf(response.id()));
            details.put("Start Date", String.valueOf(response.startDate()));
            details.put("End Date", String.valueOf(response.endDate()));
            details.put("Rotation Days", String.valueOf(response.rotationDays()));
            details.put("Status", response.status());
            details.put("Platoon Mappings", String.valueOf(response.platoonSections().size()));

            return ConfirmationResponseData.builder()
                    .success(true)
                    .message("Platoon rotation cycle created successfully from "
                            + response.startDate() + " to " + response.endDate()
                            + " (" + response.rotationDays() + " days).")
                    .details(details)
                    .build();
        }

        // Parameters incomplete — auto-generate platoon-section mappings
        // Determine next rotation based on previous cycles (per-platoon shift)
        List<Long> sectionIdsList = List.of(3L, 4L, 5L, 6L, 7L);
        Map<Integer, Long> autoGeneratedMappings = new LinkedHashMap<>();

        try {
            var allCycles = cycleService.getCycles(null);
            if (allCycles != null && !allCycles.isEmpty()) {
                // Find the most recent cycle and its platoon-section mappings
                CycleResponse lastCycle = allCycles.get(0); // sorted by date desc
                if (lastCycle.platoonSections() != null && !lastCycle.platoonSections().isEmpty()) {
                    // For each platoon, shift its section by +1 in circular order
                    for (PlatoonSectionMapping m : lastCycle.platoonSections()) {
                        int platoonIdx = m.platoonId().intValue();
                        if (!m.sectionIds().isEmpty()) {
                            Long lastSection = m.sectionIds().get(0);
                            int lastIdx = sectionIdsList.indexOf(lastSection);
                            if (lastIdx < 0) lastIdx = platoonIdx - 1;
                            int nextIdx = (lastIdx + 1) % 5;
                            autoGeneratedMappings.put(platoonIdx, sectionIdsList.get(nextIdx));
                        } else {
                            autoGeneratedMappings.put(platoonIdx, sectionIdsList.get(platoonIdx - 1));
                        }
                    }
                    // Fill any missing platoons
                    for (int i = 1; i <= 5; i++) {
                        autoGeneratedMappings.putIfAbsent(i, sectionIdsList.get(i - 1));
                    }
                } else {
                    for (int i = 0; i < 5; i++) {
                        autoGeneratedMappings.put(i + 1, sectionIdsList.get(i));
                    }
                }
            } else {
                for (int i = 0; i < 5; i++) {
                    autoGeneratedMappings.put(i + 1, sectionIdsList.get(i));
                }
            }
        } catch (Exception e) {
            for (int i = 0; i < 5; i++) {
                autoGeneratedMappings.put(i + 1, sectionIdsList.get(i));
            }
        }

        // Map section IDs to names for form display
        List<String> sectionNames = List.of("Section C", "Section D", "Section E", "Section F", "Section G");
        Map<Long, String> sectionIdToName = new LinkedHashMap<>();
        sectionIdToName.put(3L, "Section C");
        sectionIdToName.put(4L, "Section D");
        sectionIdToName.put(5L, "Section E");
        sectionIdToName.put(6L, "Section F");
        sectionIdToName.put(7L, "Section G");

        List<String> sectionOptions = List.of(
                "Section C", "Section D", "Section E", "Section F", "Section G");

        List<FormResponseData.FieldDef> fields = new ArrayList<>();
        
        // Get next available date for the start date default
        LocalDate nextAvailableDate = cycleService.getNextAvailableDate();
        String defaultStartDate = startDateStr != null ? startDateStr : nextAvailableDate.toString();
        
        fields.add(FormResponseData.FieldDef.builder()
                .name("startDate").label("Start Date").type("date")
                .required(true).value(defaultStartDate).build());
        fields.add(FormResponseData.FieldDef.builder()
                .name("rotationDays").label("Rotation Days").type("number")
                .required(true).value(rotationDaysObj != null ? String.valueOf(rotationDaysObj) : "15").build());

        // 5 platoon rows with pre-filled section from auto-generation
        var platoons = platoonRepository.findAllByOrderByBaseOffsetAsc();
        for (int i = 0; i < 5; i++) {
            String platoonLabel = platoons.size() > i
                    ? "Platoon " + platoons.get(i).getName()
                    : "Platoon " + (i + 1);
            String platoonId = platoons.size() > i
                    ? String.valueOf(platoons.get(i).getId())
                    : String.valueOf(i + 1);
            // Pre-fill with auto-generated mapping
            Long autoSection = autoGeneratedMappings.getOrDefault(i + 1, sectionIdsList.get(i));
            String autoSectionName = sectionIdToName.getOrDefault(autoSection, "Section C");

            fields.add(FormResponseData.FieldDef.builder()
                    .name("platoon_" + platoonId + "_sections")
                    .label(platoonLabel)
                    .type("select")
                    .required(true)
                    .value(autoSectionName)
                    .options(sectionOptions)
                    .build());
        }

        return FormResponseData.builder()
                .formId("create-cycle-" + System.currentTimeMillis())
                .title("Create Platoon Rotation Cycle (Auto-Generated)")
                .submitAction("create_cycle")
                .submitLabel("Create Cycle")
                .fields(fields)
                .build();
    }

    /**
     * Lists cycles with optional status filter. Returns a table.
     */
    private Object executeListCycles(Map<String, Object> params) {
        String status = (String) params.get("status");
        List<CycleResponse> cycles = cycleService.getCycles(status);

        if (cycles.isEmpty()) {
            return TableResponseData.builder()
                    .columns(List.of())
                    .rows(List.of())
                    .totalCount(0)
                    .meta(Map.of("message", "No cycles found" + (status != null ? " with status: " + status : "")))
                    .build();
        }

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("id").label("ID").build(),
                TableResponseData.ColumnDef.builder().key("startDate").label("Start Date").build(),
                TableResponseData.ColumnDef.builder().key("endDate").label("End Date").build(),
                TableResponseData.ColumnDef.builder().key("rotationDays").label("Rotation Days").build(),
                TableResponseData.ColumnDef.builder().key("status").label("Status").build()
        );

        List<Map<String, Object>> rows = cycles.stream()
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", c.id());
                    row.put("startDate", String.valueOf(c.startDate()));
                    row.put("endDate", String.valueOf(c.endDate()));
                    row.put("rotationDays", c.rotationDays());
                    row.put("status", c.status());
                    return row;
                })
                .collect(Collectors.toList());

        return TableResponseData.builder()
                .columns(columns)
                .rows(rows)
                .totalCount(cycles.size())
                .build();
    }

    /**
     * Gets cycle details by ID, returns formatted table of details.
     */
    private Object executeGetCycleDetails(Map<String, Object> params) {
        Long cycleId = toLong(params.get("cycle_id"));
        if (cycleId == null) {
            return McpToolResult.failure("VALIDATION_ERROR", "cycle_id is required");
        }

        CycleResponse cycle = cycleService.getCycleById(cycleId);

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("field").label("Field").build(),
                TableResponseData.ColumnDef.builder().key("value").label("Details").build()
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("field", "Cycle ID", "value", cycle.id()));
        rows.add(Map.of("field", "Start Date", "value", String.valueOf(cycle.startDate())));
        rows.add(Map.of("field", "End Date", "value", String.valueOf(cycle.endDate())));
        rows.add(Map.of("field", "Rotation Days", "value", cycle.rotationDays()));
        rows.add(Map.of("field", "Status", "value", cycle.status()));
        rows.add(Map.of("field", "Created At", "value",
                cycle.createdAt() != null ? String.valueOf(cycle.createdAt()) : "—"));
        rows.add(Map.of("field", "Updated At", "value",
                cycle.updatedAt() != null ? String.valueOf(cycle.updatedAt()) : "—"));

        // Add platoon-section mapping details
        if (cycle.platoonSections() != null) {
            for (PlatoonSectionMapping mapping : cycle.platoonSections()) {
                rows.add(Map.of("field", "Platoon " + mapping.platoonId() + " Sections",
                        "value", mapping.sectionIds().toString()));
            }
        }

        return TableResponseData.builder()
                .columns(columns)
                .rows(rows)
                .totalCount(rows.size())
                .build();
    }

    /**
     * Partially updates a cycle with provided fields.
     */
    @SuppressWarnings("unchecked")
    private Object executeUpdateCycle(Map<String, Object> params) {
        Long cycleId = toLong(params.get("cycle_id"));
        if (cycleId == null) {
            return McpToolResult.failure("VALIDATION_ERROR", "cycle_id is required");
        }

        String startDateStr = (String) params.get("start_date");
        Object rotationDaysObj = params.get("rotation_days");
        String status = (String) params.get("status");
        Object platoonSectionsObj = params.get("platoon_sections");

        LocalDate startDate = startDateStr != null ? LocalDate.parse(startDateStr) : null;
        Integer rotationDays = rotationDaysObj != null ? toInteger(rotationDaysObj) : null;
        List<PlatoonSectionMapping> mappings = platoonSectionsObj != null
                ? parsePlatoonSections(platoonSectionsObj) : null;

        CycleUpdateRequest request = new CycleUpdateRequest(startDate, rotationDays, mappings, status);
        CycleResponse response = cycleService.partialUpdateCycle(cycleId, request);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Cycle ID", String.valueOf(response.id()));
        details.put("Start Date", String.valueOf(response.startDate()));
        details.put("End Date", String.valueOf(response.endDate()));
        details.put("Rotation Days", String.valueOf(response.rotationDays()));
        details.put("Status", response.status());

        return ConfirmationResponseData.builder()
                .success(true)
                .message("Cycle " + cycleId + " updated successfully.")
                .details(details)
                .build();
    }

    /**
     * Soft-deletes a cycle.
     */
    private Object executeDeleteCycle(Map<String, Object> params) {
        Long cycleId = toLong(params.get("cycle_id"));
        if (cycleId == null) {
            return McpToolResult.failure("VALIDATION_ERROR", "cycle_id is required");
        }

        cycleService.deleteCycle(cycleId);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Cycle ID", String.valueOf(cycleId));
        details.put("Action", "Soft-deleted (status set to DELETED)");

        return ConfirmationResponseData.builder()
                .success(true)
                .message("Cycle " + cycleId + " has been deleted successfully.")
                .details(details)
                .build();
    }

    /**
     * Reassigns a duty assignment to a different person.
     * Validates that the replacement is not on leave and has no conflicting assignment.
     */
    private Object executeReassignDuty(Map<String, Object> params) {
        Long assignmentId = toLong(params.get("assignment_id"));
        Long newPersonId = toLong(params.get("new_person_id"));

        if (assignmentId == null) {
            return McpToolResult.failure("VALIDATION_ERROR", "assignment_id is required");
        }
        if (newPersonId == null) {
            return McpToolResult.failure("VALIDATION_ERROR", "new_person_id is required");
        }

        // Find the assignment
        CycleDutyAssignment assignment = cycleDutyAssignmentRepository.findById(assignmentId)
                .orElse(null);
        if (assignment == null) {
            return McpToolResult.failure("NOT_FOUND", "Assignment not found with id: " + assignmentId);
        }

        // Validate replacement person exists
        Personnel replacement = personnelRepository.findById(newPersonId).orElse(null);
        if (replacement == null) {
            return McpToolResult.failure("NOT_FOUND", "Personnel not found with id: " + newPersonId);
        }

        // Check if replacement is on leave
        if (leaveConflictDetector.isOnLeave(newPersonId, assignment.getDate())) {
            return ConfirmationResponseData.builder()
                    .success(false)
                    .message("Replacement person is on approved leave for " + assignment.getDate()
                            + ". Please select someone else.")
                    .details(Map.of(
                            "Replacement", replacement.getPersonName(),
                            "Date", String.valueOf(assignment.getDate()),
                            "Reason", "On approved leave"))
                    .build();
        }

        // Check for duplicate assignment (same cycle, date, section)
        List<CycleDutyAssignment> existing = cycleDutyAssignmentRepository
                .findByCycleIdAndDateAndSectionIdAndPersonId(
                        assignment.getCycleId(), assignment.getDate(),
                        assignment.getSectionId(), newPersonId);
        if (!existing.isEmpty()) {
            return ConfirmationResponseData.builder()
                    .success(false)
                    .message("Replacement person already has an assignment for the same cycle, date, and section.")
                    .details(Map.of(
                            "Replacement", replacement.getPersonName(),
                            "Date", String.valueOf(assignment.getDate()),
                            "Reason", "Duplicate assignment"))
                    .build();
        }

        // Perform the reassignment
        Long originalPersonId = assignment.getPersonId();
        assignment.setPersonId(newPersonId);
        assignment.setIsOverride(true);
        assignment.setUpdatedAt(LocalDateTime.now());
        cycleDutyAssignmentRepository.save(assignment);

        // Look up original person's name for the response
        String originalName = personnelRepository.findById(originalPersonId)
                .map(Personnel::getPersonName)
                .orElse("Unknown");

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Assignment ID", String.valueOf(assignmentId));
        details.put("Date", String.valueOf(assignment.getDate()));
        details.put("Original Person", originalName);
        details.put("New Person", replacement.getPersonName());
        details.put("Is Override", "true");

        return ConfirmationResponseData.builder()
                .success(true)
                .message("Duty reassigned successfully from " + originalName
                        + " to " + replacement.getPersonName() + " on " + assignment.getDate() + ".")
                .details(details)
                .build();
    }

    /**
     * Gets cycle activity/audit logs. If cycle_id provided, returns for that cycle only.
     */
    private Object executeGetCycleActivities(Map<String, Object> params) {
        Long cycleId = toLong(params.get("cycle_id"));

        List<com.policescheduler.entity.CycleAuditLog> logs;
        if (cycleId != null) {
            logs = cycleAuditService.getAuditLogByCycle(cycleId);
        } else {
            logs = cycleAuditService.getAllAuditLogs();
        }

        if (logs.isEmpty()) {
            return TableResponseData.builder()
                    .columns(List.of())
                    .rows(List.of())
                    .totalCount(0)
                    .meta(Map.of("message", "No activity logs found" + (cycleId != null ? " for cycle #" + cycleId : "")))
                    .build();
        }

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("cycleId").label("Cycle").build(),
                TableResponseData.ColumnDef.builder().key("action").label("Action").build(),
                TableResponseData.ColumnDef.builder().key("description").label("Description").build(),
                TableResponseData.ColumnDef.builder().key("timestamp").label("Timestamp").build()
        );

        // Show most recent 20 logs
        List<Map<String, Object>> rows = logs.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(20)
                .map(log -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("cycleId", "Cycle #" + log.getCycleId());
                    row.put("action", log.getAction().replace("_", " "));
                    row.put("description", log.getDescription());
                    row.put("timestamp", log.getCreatedAt().toString());
                    return row;
                })
                .collect(Collectors.toList());

        return TableResponseData.builder()
                .columns(columns)
                .rows(rows)
                .totalCount(logs.size())
                .meta(Map.of("description", "Showing latest 20 of " + logs.size() + " activity records"))
                .build();
    }

    /**
     * Auto-reassigns a duty to a randomly available person from another platoon.
     * No need for user to specify the replacement — system picks one.
     */
    private Object executeAutoReassignDuty(Map<String, Object> params) {
        Long assignmentId = toLong(params.get("assignment_id"));

        if (assignmentId == null) {
            return McpToolResult.failure("VALIDATION_ERROR", "assignment_id is required");
        }

        // Find the assignment
        CycleDutyAssignment assignment = cycleDutyAssignmentRepository.findById(assignmentId)
                .orElse(null);
        if (assignment == null) {
            return ConfirmationResponseData.builder()
                    .success(false)
                    .message("Assignment not found with ID: " + assignmentId)
                    .details(Map.of())
                    .build();
        }

        Long currentPlatoonId = assignment.getPlatoonId();
        Long oldPersonId = assignment.getPersonId();
        LocalDate date = assignment.getDate();

        // Get all person IDs already assigned for this date in this cycle
        List<CycleDutyAssignment> existingAssignments = cycleDutyAssignmentRepository
                .findByCycleIdAndDate(assignment.getCycleId(), date);
        Set<Long> alreadyAssignedIds = existingAssignments.stream()
                .map(CycleDutyAssignment::getPersonId)
                .collect(Collectors.toSet());

        // Find available personnel from OTHER platoons
        List<Personnel> candidates = personnelRepository.findByIsActiveTrue().stream()
                .filter(p -> p.getPlatoonId() != null && !p.getPlatoonId().equals(currentPlatoonId))
                .filter(p -> !p.getId().equals(oldPersonId))
                .filter(p -> !alreadyAssignedIds.contains(p.getId()))
                .filter(p -> !leaveConflictDetector.isOnLeave(p.getId(), date))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return ConfirmationResponseData.builder()
                    .success(false)
                    .message("No available personnel from other platoons for auto-reassignment on " + date)
                    .details(Map.of("Assignment ID", String.valueOf(assignmentId), "Date", String.valueOf(date)))
                    .build();
        }

        // Randomly pick one
        Personnel chosen = candidates.get(new java.util.Random().nextInt(candidates.size()));

        // Update the assignment
        Long originalPersonId = assignment.getPersonId();
        assignment.setPersonId(chosen.getId());
        assignment.setIsOverride(true);
        assignment.setUpdatedAt(LocalDateTime.now());
        cycleDutyAssignmentRepository.save(assignment);

        // Look up original person's name
        String originalName = personnelRepository.findById(originalPersonId)
                .map(Personnel::getPersonName)
                .orElse("Person #" + originalPersonId);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Assignment ID", String.valueOf(assignmentId));
        details.put("Date", String.valueOf(date));
        details.put("Original Person", originalName);
        details.put("New Person", chosen.getPersonName() + " (" + chosen.getBadgeId() + ")");
        details.put("New Person Platoon", String.valueOf(chosen.getPlatoonId()));
        details.put("Reason", "Auto-assigned due to leave conflict");

        return ConfirmationResponseData.builder()
                .success(true)
                .message("Duty auto-reassigned from " + originalName + " to "
                        + chosen.getPersonName() + " (" + chosen.getBadgeId() + ") from Platoon "
                        + chosen.getPlatoonId() + " on " + date + ".")
                .details(details)
                .build();
    }

    // --- Helper methods ---

    @SuppressWarnings("unchecked")
    private List<PlatoonSectionMapping> parsePlatoonSections(Object platoonSectionsObj) {
        if (platoonSectionsObj instanceof List<?> list) {
            return list.stream()
                    .map(item -> {
                        if (item instanceof Map<?, ?> map) {
                            Long platoonId = toLong(map.get("platoon_id"));
                            Object sectionIdsObj = map.get("section_ids");
                            List<Long> sectionIds = parseLongList(sectionIdsObj);
                            return new PlatoonSectionMapping(platoonId, sectionIds);
                        }
                        throw new IllegalArgumentException("Invalid platoon_sections format");
                    })
                    .collect(Collectors.toList());
        }
        throw new IllegalArgumentException("platoon_sections must be a list");
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseLongList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream()
                    .map(this::toLong)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
