package com.policescheduler.chat.executor;

import com.policescheduler.chat.model.ProcessingContext;
import com.policescheduler.dto.CreateLeaveRequest;
import com.policescheduler.dto.PersonnelDto;
import com.policescheduler.dto.PersonnelFilter;
import com.policescheduler.dto.chat.ConfirmationResponseData;
import com.policescheduler.dto.chat.FormResponseData;
import com.policescheduler.dto.chat.SuggestionsResponseData;
import com.policescheduler.dto.chat.TableResponseData;
import com.policescheduler.entity.DutyType;
import com.policescheduler.entity.Personnel;
import com.policescheduler.repository.DutyTypeRepository;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.SectionRepository;
import com.policescheduler.service.LeaveService;
import com.policescheduler.service.PersonnelService;
import com.policescheduler.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DomainDbMcpServer implements McpServer {

    private static final Logger log = LoggerFactory.getLogger(DomainDbMcpServer.class);

    private final PersonnelService personnelService;
    private final ScheduleService scheduleService;
    private final LeaveService leaveService;
    private final SectionRepository sectionRepository;
    private final PersonnelRepository personnelRepository;
    private final DutyTypeRepository dutyTypeRepository;

    public DomainDbMcpServer(PersonnelService personnelService,
                             ScheduleService scheduleService,
                             LeaveService leaveService,
                             SectionRepository sectionRepository,
                             PersonnelRepository personnelRepository,
                             DutyTypeRepository dutyTypeRepository) {
        this.personnelService = personnelService;
        this.scheduleService = scheduleService;
        this.leaveService = leaveService;
        this.sectionRepository = sectionRepository;
        this.personnelRepository = personnelRepository;
        this.dutyTypeRepository = dutyTypeRepository;
    }

    @Override
    public String getServerId() {
        return "domain-db";
    }

    @Override
    public List<McpToolDefinition> listTools() {
        return List.of(
            new McpToolDefinition("search_personnel", "Search for personnel by name, badge ID, or keyword", Map.of()),
            new McpToolDefinition("search_by_duty_type", "Search personnel by duty type", Map.of()),
            new McpToolDefinition("search_by_designation", "Search personnel by designation/rank", Map.of()),
            new McpToolDefinition("list_section_personnel", "List all personnel in a specific section", Map.of()),
            new McpToolDefinition("get_person_by_badge", "Get detailed info about a person by badge ID", Map.of()),
            new McpToolDefinition("get_schedule_overview", "Get current schedule overview", Map.of()),
            new McpToolDefinition("get_leave_count", "Get leave request summary", Map.of()),
            new McpToolDefinition("get_platoon_rotation", "Get current platoon rotation", Map.of()),
            new McpToolDefinition("add_person", "Add a new police personnel", Map.of()),
            new McpToolDefinition("create_leave", "Create a new leave request", Map.of()),
            new McpToolDefinition("filter_personnel", "Filter personnel with combined filters: section + designation + duty_type + active", Map.of()),
            new McpToolDefinition("get_leave_details", "Get detailed leave list filtered by date range, type, or status", Map.of()),
            new McpToolDefinition("get_today_duties", "Get today's specific duty assignments by duty type", Map.of()),
            new McpToolDefinition("update_personnel", "Update a personnel's section, duty, or designation", Map.of()),
            new McpToolDefinition("get_strength_summary", "Get personnel count by designation for a section or overall", Map.of()),
            new McpToolDefinition("search_drivers", "Search drivers with vehicle/PDMS/licence filters", Map.of()),
            new McpToolDefinition("get_duty_types", "List all duty types/posts, optionally filtered by section", Map.of()),
            new McpToolDefinition("add_duty_type", "Add a new duty type/post", Map.of()),
            new McpToolDefinition("update_duty_type", "Update an existing duty type", Map.of()),
            new McpToolDefinition("delete_duty_type", "Delete a duty type/post", Map.of())
        );
    }

    @Override
    public McpToolResult executeTool(String toolName, Map<String, Object> parameters, ProcessingContext context) {
        log.info("DomainDbMcpServer executing tool: {} with params: {}", toolName, parameters);
        try {
            Object result = switch (toolName) {
                case "search_personnel" -> executeSearchPersonnel(parameters);
                case "search_by_duty_type" -> executeSearchByDutyType(parameters);
                case "search_by_designation" -> executeSearchByDesignation(parameters);
                case "list_section_personnel" -> executeListSectionPersonnel(parameters);
                case "get_person_by_badge" -> executeGetPersonByBadge(parameters);
                case "get_schedule_overview" -> executeGetScheduleOverview();
                case "get_leave_count" -> executeGetLeaveCount();
                case "get_platoon_rotation" -> executeGetPlatoonRotation();
                case "add_person" -> executeAddPerson(parameters);
                case "create_leave" -> executeCreateLeave(parameters);
                case "filter_personnel" -> executeFilterPersonnel(parameters);
                case "get_leave_details" -> executeGetLeaveDetails(parameters);
                case "get_today_duties" -> executeGetTodayDuties(parameters);
                case "update_personnel" -> executeUpdatePersonnel(parameters);
                case "get_strength_summary" -> executeGetStrengthSummary(parameters);
                case "search_drivers" -> executeSearchDriversFiltered(parameters);
                case "get_duty_types" -> executeGetDutyTypes(parameters);
                case "add_duty_type" -> executeAddDutyType(parameters);
                case "update_duty_type" -> executeUpdateDutyType(parameters);
                case "delete_duty_type" -> executeDeleteDutyType(parameters);
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };
            return McpToolResult.success(result);
        } catch (Exception e) {
            log.error("Tool execution error for {}: {}", toolName, e.getMessage(), e);
            return McpToolResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }

    private Object executeSearchPersonnel(Map<String, Object> params) {
        String query = (String) params.getOrDefault("query", "");
        String normalized = query.toLowerCase().trim();
        if (normalized.isEmpty() || normalized.matches(".*(all|list|everyone|every one|persons|personnel|show all|all persons).*")) {
            query = "";
        }
        PersonnelFilter filter = new PersonnelFilter();
        if (!query.isEmpty()) {
            filter.setSearch(query);
        }
        Page<PersonnelDto> results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
        if (results.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No personnel found" + (query.isEmpty() ? "." : " matching: " + query + "."))
                    .suggestions(List.of("List all personnel", "Search by name", "Show Section A personnel"))
                    .build();
        }
        return buildPersonnelTable(results);
    }

    private Object executeSearchByDutyType(Map<String, Object> params) {
        String dutyType = (String) params.getOrDefault("duty_type", "");
        if (dutyType.isBlank()) {
            return SuggestionsResponseData.builder()
                    .message("Please specify a duty type to search for.")
                    .suggestions(List.of("Show guard duty list", "Show strike force list", "Show check post list"))
                    .build();
        }

        // Special case: DRIVER — drivers are identified by having vehicle_number, not by duty_type
        if ("DRIVER".equalsIgnoreCase(dutyType)) {
            return executeSearchDrivers();
        }

        // First try exact match (case-insensitive)
        PersonnelFilter filter = new PersonnelFilter();
        filter.setDutyType(dutyType);
        Page<PersonnelDto> results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
        // Fallback: search as keyword (catches case mismatches and partial matches)
        if (results.isEmpty()) {
            filter = new PersonnelFilter();
            filter.setSearch(dutyType);
            results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
        }
        // Second fallback: try with underscores replaced by spaces
        if (results.isEmpty()) {
            String altDutyType = dutyType.replace("_", " ");
            if (!altDutyType.equals(dutyType)) {
                filter = new PersonnelFilter();
                filter.setSearch(altDutyType);
                results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
            }
        }
        if (results.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No personnel found with duty type: " + dutyType)
                    .suggestions(List.of("List all personnel", "Show guard duty list", "Show strike force list"))
                    .build();
        }
        return buildPersonnelTable(results);
    }

    private Object executeSearchByDesignation(Map<String, Object> params) {
        String designation = (String) params.getOrDefault("designation", "");
        if (designation.isBlank()) {
            return SuggestionsResponseData.builder()
                    .message("Please specify a designation to search for.")
                    .suggestions(List.of("List all DCP", "List all ACP", "List all RSI"))
                    .build();
        }
        PersonnelFilter filter = new PersonnelFilter();
        filter.setDesignation(designation.toUpperCase());
        Page<PersonnelDto> results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
        if (results.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No personnel found with designation: " + designation)
                    .suggestions(List.of("List all DCP", "List all ACP", "List all RSI", "List all AHC", "List all APC"))
                    .build();
        }
        return buildPersonnelTable(results);
    }

    private Object executeListSectionPersonnel(Map<String, Object> params) {
        String section = (String) params.getOrDefault("section", "A");
        section = section.replaceAll("(?i)section\\s*", "").trim().toUpperCase();
        if (section.isEmpty()) section = "A";
        PersonnelFilter filter = new PersonnelFilter();
        filter.setSection(section);
        Page<PersonnelDto> results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
        if (results.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No personnel found in Section " + section + ".")
                    .suggestions(List.of("List all personnel", "Show Section A personnel", "Show Section B personnel"))
                    .build();
        }
        return buildPersonnelTable(results);
    }

    private Object executeGetPersonByBadge(Map<String, Object> params) {
        String badgeId = (String) params.get("badge_id");
        if (badgeId == null || badgeId.isBlank()) {
            return SuggestionsResponseData.builder()
                    .message("Please provide a badge ID or name.")
                    .suggestions(List.of("badge DCP-01", "Search by name"))
                    .build();
        }
        // Normalize badge ID: user might say "AHC 2649" instead of "AHC-2649"
        String normalizedBadge = badgeId.trim().toUpperCase();
        if (normalizedBadge.matches("[A-Z]+\\s+\\d+")) {
            normalizedBadge = normalizedBadge.replaceFirst("\\s+", "-");
        }

        // First try exact badge lookup
        try {
            PersonnelDto p = personnelService.getByBadgeId(normalizedBadge);
            return buildPersonDetails(p);
        } catch (Exception e) {
            // Badge not found — try searching by name
            log.debug("Badge '{}' not found, trying name search: {}", normalizedBadge, badgeId);
        }

        // Fallback: search by name
        PersonnelFilter filter = new PersonnelFilter();
        filter.setSearch(badgeId.trim());
        Page<PersonnelDto> results = personnelService.listPersonnel(filter, PageRequest.of(0, 10));

        if (results.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("Person not found with badge or name: " + badgeId)
                    .suggestions(List.of("List all personnel", "Search by name"))
                    .build();
        }

        // If exactly one match, show detailed view
        if (results.getTotalElements() == 1) {
            return buildPersonDetails(results.getContent().get(0));
        }

        // Multiple matches — show as table
        return buildPersonnelTable(results);
    }

    private Object buildPersonDetails(PersonnelDto p) {
        // Resolve section name
        String sectionName = p.getSection();
        if (sectionName != null) {
            var sectionOpt = sectionRepository.findByCode(sectionName);
            if (sectionOpt.isPresent()) {
                sectionName = sectionOpt.get().getName() + " (" + p.getSection() + ")";
            }
        }
        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("field").label("Field").build(),
                TableResponseData.ColumnDef.builder().key("value").label("Details").build()
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("field", "Name", "value", p.getPersonName() != null ? p.getPersonName() : "—"));
        rows.add(Map.of("field", "Badge ID", "value", p.getBadgeId() != null ? p.getBadgeId() : "—"));
        rows.add(Map.of("field", "Designation", "value", p.getDesignation() != null ? p.getDesignation() : "—"));
        rows.add(Map.of("field", "Section", "value", sectionName != null ? sectionName : "—"));
        rows.add(Map.of("field", "Duty Type", "value", p.getDutyType() != null ? p.getDutyType() : "—"));
        rows.add(Map.of("field", "Location", "value", p.getLocation() != null ? p.getLocation() : "—"));
        rows.add(Map.of("field", "Phone", "value", p.getPhoneNumber() != null ? p.getPhoneNumber() : "—"));
        rows.add(Map.of("field", "Email", "value", p.getEmail() != null ? p.getEmail() : "—"));
        rows.add(Map.of("field", "Status", "value", Boolean.TRUE.equals(p.getIsActive()) ? "Active" : "Inactive"));
        return TableResponseData.builder().columns(columns).rows(rows).totalCount(rows.size()).build();
    }

    private Object executeGetScheduleOverview() {
        // Get current cycle assignment data to show meaningful platoon deployment info
        var sections = sectionRepository.findByIsActiveTrueOrderBySortOrder();
        Map<String, String> sectionNameMap = new HashMap<>();
        sections.forEach(s -> sectionNameMap.put(s.getCode(), s.getName()));

        var overview = scheduleService.getSectionsOverview();
        int a = overview.getSectionA() != null ? overview.getSectionA().size() : 0;
        int b = overview.getSectionB() != null ? overview.getSectionB().size() : 0;
        int c = overview.getSectionC() != null && overview.getSectionC().getAssignments() != null
                ? overview.getSectionC().getAssignments().size() : 0;

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("section").label("Section").build(),
                TableResponseData.ColumnDef.builder().key("name").label("Name").build(),
                TableResponseData.ColumnDef.builder().key("type").label("Type").build(),
                TableResponseData.ColumnDef.builder().key("assignments").label("Assignments").build()
        );
        List<Map<String, Object>> rows = List.of(
                Map.of("section", "A", "name", sectionNameMap.getOrDefault("A", "Section A"), "type", "Fixed Duties", "assignments", a),
                Map.of("section", "B", "name", sectionNameMap.getOrDefault("B", "Section B"), "type", "Office/Support", "assignments", b),
                Map.of("section", "C", "name", sectionNameMap.getOrDefault("C", "Section C"), "type", "Rotational", "assignments", c),
                Map.of("section", "—", "name", "Total", "type", "—", "assignments", a + b + c)
        );

        // Include platoon rotation info in meta
        Map<String, Object> meta = new LinkedHashMap<>();
        try {
            var rotation = scheduleService.getPlatoonRotation();
            if (rotation.getPlatoonMappings() != null) {
                StringBuilder platoonInfo = new StringBuilder();
                for (var m : rotation.getPlatoonMappings()) {
                    if (!platoonInfo.isEmpty()) platoonInfo.append(" | ");
                    platoonInfo.append(m.getPlatoonName()).append(" → ").append(m.getCurrentDutyType());
                }
                meta.put("Current Platoon Deployment", platoonInfo.toString());
                meta.put("Next Rotation", String.valueOf(rotation.getNextRotationDate()));
            }
        } catch (Exception e) {
            log.debug("Could not get platoon rotation for schedule overview: {}", e.getMessage());
        }

        return TableResponseData.builder().columns(columns).rows(rows).totalCount(rows.size()).meta(meta).build();
    }

    private Object executeGetLeaveCount() {
        var leaves = leaveService.listRequests(null, PageRequest.of(0, 1000), "admin", "ADMIN");
        long pending = leaves.getContent().stream().filter(l -> "PENDING".equals(l.getStatus())).count();
        long approved = leaves.getContent().stream().filter(l -> "APPROVED".equals(l.getStatus())).count();
        long rejected = leaves.getContent().stream().filter(l -> "REJECTED".equals(l.getStatus())).count();
        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("category").label("Category").build(),
                TableResponseData.ColumnDef.builder().key("count").label("Count").build()
        );
        List<Map<String, Object>> rows = List.of(
                Map.of("category", "Total Requests", "count", leaves.getTotalElements()),
                Map.of("category", "Pending", "count", pending),
                Map.of("category", "Approved", "count", approved),
                Map.of("category", "Rejected", "count", rejected)
        );
        return TableResponseData.builder().columns(columns).rows(rows).totalCount(rows.size()).build();
    }

    private Object executeGetPlatoonRotation() {
        var rotation = scheduleService.getPlatoonRotation();
        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("platoon").label("Platoon").build(),
                TableResponseData.ColumnDef.builder().key("dutyType").label("Current Duty").build(),
                TableResponseData.ColumnDef.builder().key("section").label("Section").build()
        );

        // Build section code → name map
        Map<String, String> sectionNameMap = new HashMap<>();
        sectionRepository.findByIsActiveTrueOrderBySortOrder().forEach(s ->
                sectionNameMap.put(s.getCode(), s.getName()));

        List<Map<String, Object>> rows = new ArrayList<>();
        if (rotation.getPlatoonMappings() != null) {
            rotation.getPlatoonMappings().forEach(m -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("platoon", m.getPlatoonName());
                row.put("dutyType", m.getCurrentDutyType());
                // Map the section code to its full name — platoon sections are typically C-G
                String sectionCode = String.valueOf((char)('C' + m.getBaseOffset()));
                String sectionName = sectionNameMap.getOrDefault(sectionCode, sectionCode);
                row.put("section", sectionName);
                rows.add(row);
            });
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("Cycle", String.valueOf(rotation.getCurrentCycleIndex() + 1));
        meta.put("Next Rotation", String.valueOf(rotation.getNextRotationDate()));
        return TableResponseData.builder().columns(columns).rows(rows).totalCount(rows.size()).meta(meta).build();
    }

    private Object executeAddPerson(Map<String, Object> params) {
        // Always show a form with pre-filled data from what the LLM extracted
        String personName = (String) params.get("person_name");
        String badgeId = (String) params.get("badge_id");
        String section = (String) params.get("section");
        String designation = (String) params.get("designation");

        List<String> designationOptions = List.of("DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC");
        List<String> sectionOptions = sectionRepository.findByIsActiveTrueOrderBySortOrder()
                .stream().map(s -> s.getCode()).toList();
        List<String> dutyTypeOptions = List.of("GUARD", "DRIVER", "OFFICE", "SUPPORT", "CHECK_POST", "PATROL",
                "DOG_SQUAD", "ASC_TEAM", "GUNMAN", "OOD", "BAND", "QRT", "CPT");

        return FormResponseData.builder()
                .formId("add-person-" + System.currentTimeMillis())
                .title("Add New Personnel")
                .submitAction("create_person")
                .fields(List.of(
                        FormResponseData.FieldDef.builder()
                                .name("personName").label("Person Name").type("text")
                                .required(true).value(personName).build(),
                        FormResponseData.FieldDef.builder()
                                .name("badgeId").label("Badge ID").type("text")
                                .required(true).value(badgeId).build(),
                        FormResponseData.FieldDef.builder()
                                .name("designation").label("Designation").type("select")
                                .required(false).value(designation).options(designationOptions).build(),
                        FormResponseData.FieldDef.builder()
                                .name("section").label("Section").type("select")
                                .required(true).value(section).options(sectionOptions).build(),
                        FormResponseData.FieldDef.builder()
                                .name("dutyType").label("Duty Type").type("select")
                                .required(false).value(null).options(dutyTypeOptions).build(),
                        FormResponseData.FieldDef.builder()
                                .name("location").label("Location").type("text")
                                .required(false).value(null).build(),
                        FormResponseData.FieldDef.builder()
                                .name("phoneNumber").label("Phone Number").type("text")
                                .required(false).value(null).build(),
                        FormResponseData.FieldDef.builder()
                                .name("email").label("Email").type("text")
                                .required(false).value(null).build(),
                        FormResponseData.FieldDef.builder()
                                .name("vehicleNumber").label("Vehicle Number").type("text")
                                .required(false).value(null).build(),
                        FormResponseData.FieldDef.builder()
                                .name("dutyLocation").label("Duty Location").type("text")
                                .required(false).value(null).build(),
                        FormResponseData.FieldDef.builder()
                                .name("pdms").label("PDMS").type("text")
                                .required(false).value(null).build(),
                        FormResponseData.FieldDef.builder()
                                .name("licenceType").label("Licence Type").type("text")
                                .required(false).value(null).build(),
                        FormResponseData.FieldDef.builder()
                                .name("deployedFrom").label("Deployed From").type("text")
                                .required(false).value(null).build()
                ))
                .build();
    }

    private Object executeCreateLeave(Map<String, Object> params) {
        String badgeId = (String) params.get("badge_id");
        String leaveTypeParam = (String) params.get("leave_type");
        String startDateParam = (String) params.get("start_date");
        String endDateParam = (String) params.get("end_date");
        String reason = (String) params.get("reason");

        // Resolve personnel: try badge ID first, then name search
        Personnel resolvedPerson = null;
        if (badgeId != null && !badgeId.isBlank()) {
            // Normalize badge: "AHC 2649" → "AHC-2649"
            String normalizedBadge = badgeId.trim().toUpperCase();
            if (normalizedBadge.matches("[A-Z]+\\s+\\d+")) {
                normalizedBadge = normalizedBadge.replaceFirst("\\s+", "-");
            }
            resolvedPerson = personnelRepository.findByBadgeId(normalizedBadge).orElse(null);

            if (resolvedPerson == null) {
                // Try searching by name
                List<Personnel> byName = personnelRepository.findByIsActiveTrue().stream()
                        .filter(p -> p.getPersonName() != null && p.getPersonName().toLowerCase().contains(badgeId.toLowerCase()))
                        .toList();
                if (byName.size() == 1) {
                    resolvedPerson = byName.get(0);
                } else if (byName.size() > 1) {
                    // Multiple matches — show form with only these matches as options
                    List<String> matchedOptions = byName.stream()
                            .map(p -> p.getId() + " - " + p.getPersonName() + " (" + p.getBadgeId() + ")")
                            .toList();

                    List<String> leaveTypes = List.of(
                            "CASUAL_LEAVE", "SICK_LEAVE", "EARNED_LEAVE", "WEEKLY_OFF", "COMPENSATORY_OFF");

                    return FormResponseData.builder()
                            .formId("create-leave-" + System.currentTimeMillis())
                            .title("Create Leave Request")
                            .submitAction("create_leave")
                            .submitLabel("Submit Leave")
                            .fields(List.of(
                                    FormResponseData.FieldDef.builder()
                                            .name("personnelId").label("Personnel (multiple matches for '" + badgeId + "')").type("select")
                                            .required(true).value(null).options(matchedOptions).build(),
                                    FormResponseData.FieldDef.builder()
                                            .name("leaveType").label("Leave Type").type("select")
                                            .required(true).value(leaveTypeParam).options(leaveTypes).build(),
                                    FormResponseData.FieldDef.builder()
                                            .name("startDate").label("Start Date").type("date")
                                            .required(true).value(startDateParam).build(),
                                    FormResponseData.FieldDef.builder()
                                            .name("endDate").label("End Date").type("date")
                                            .required(true).value(endDateParam != null ? endDateParam : startDateParam).build(),
                                    FormResponseData.FieldDef.builder()
                                            .name("reason").label("Reason").type("text")
                                            .required(false).value(reason).build()
                            ))
                            .build();
                }
            }
        }

        // ALWAYS show a pre-filled form for user confirmation — never create directly
        List<Personnel> activePersonnel = personnelRepository.findByIsActiveTrue();
        List<String> personnelOptions = activePersonnel.stream()
                .map(p -> p.getId() + " - " + p.getPersonName() + " (" + p.getBadgeId() + ")")
                .toList();

        // Pre-select the resolved person in the dropdown
        String preselectedPersonnel = null;
        if (resolvedPerson != null) {
            preselectedPersonnel = resolvedPerson.getId() + " - " + resolvedPerson.getPersonName() + " (" + resolvedPerson.getBadgeId() + ")";
        }

        List<String> leaveTypeOptions = List.of(
                "CASUAL_LEAVE", "SICK_LEAVE", "EARNED_LEAVE", "WEEKLY_OFF", "COMPENSATORY_OFF");

        return FormResponseData.builder()
                .formId("create-leave-" + System.currentTimeMillis())
                .title("Create Leave Request")
                .submitAction("create_leave")
                .submitLabel("Submit Leave")
                .fields(List.of(
                        FormResponseData.FieldDef.builder()
                                .name("personnelId").label("Personnel").type("select")
                                .required(true).value(preselectedPersonnel).options(personnelOptions).build(),
                        FormResponseData.FieldDef.builder()
                                .name("leaveType").label("Leave Type").type("select")
                                .required(true).value(leaveTypeParam).options(leaveTypeOptions).build(),
                        FormResponseData.FieldDef.builder()
                                .name("startDate").label("Start Date").type("date")
                                .required(true).value(startDateParam).build(),
                        FormResponseData.FieldDef.builder()
                                .name("endDate").label("End Date").type("date")
                                .required(true).value(endDateParam != null ? endDateParam : startDateParam).build(),
                        FormResponseData.FieldDef.builder()
                                .name("reason").label("Reason").type("text")
                                .required(false).value(reason).build()
                ))
                .build();
    }

    private Object executeSearchDrivers() {
        List<Personnel> drivers = personnelRepository.findMtSectionPersonnel();
        if (drivers.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No drivers found.")
                    .suggestions(List.of("List all personnel", "Show Section A personnel"))
                    .build();
        }
        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("personName").label("Name").build(),
                TableResponseData.ColumnDef.builder().key("badgeId").label("Badge ID").build(),
                TableResponseData.ColumnDef.builder().key("designation").label("Designation").build(),
                TableResponseData.ColumnDef.builder().key("dutyType").label("Duty Type").build(),
                TableResponseData.ColumnDef.builder().key("vehicleNumber").label("Vehicle").build(),
                TableResponseData.ColumnDef.builder().key("status").label("Status").build()
        );
        List<Map<String, Object>> rows = drivers.stream()
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("personName", p.getPersonName());
                    row.put("badgeId", p.getBadgeId());
                    row.put("designation", p.getDesignation());
                    row.put("dutyType", p.getDutyType() != null ? p.getDutyType() : "—");
                    row.put("vehicleNumber", p.getVehicleNumber() != null ? p.getVehicleNumber() : "—");
                    row.put("status", Boolean.TRUE.equals(p.getIsActive()) ? "Active" : "Inactive");
                    return row;
                })
                .toList();
        return TableResponseData.builder().columns(columns).rows(rows).totalCount(drivers.size()).build();
    }

    // === NEW TOOLS ===

    private Object executeFilterPersonnel(Map<String, Object> params) {
        String section = (String) params.getOrDefault("section", "");
        String designation = (String) params.getOrDefault("designation", "");
        String dutyType = (String) params.getOrDefault("duty_type", "");
        String activeStr = (String) params.getOrDefault("is_active", "true");

        PersonnelFilter filter = new PersonnelFilter();
        if (!section.isBlank()) filter.setSection(section.toUpperCase().replace("SECTION ", "").trim());
        if (!designation.isBlank()) filter.setDesignation(designation.toUpperCase().trim());
        if (!dutyType.isBlank()) filter.setDutyType(dutyType);
        filter.setIsActive("true".equalsIgnoreCase(activeStr) || activeStr.isBlank() ? true : null);

        Page<PersonnelDto> results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
        if (results.isEmpty()) {
            String desc = "";
            if (!section.isBlank()) desc += "Section " + section + " ";
            if (!designation.isBlank()) desc += designation + " ";
            if (!dutyType.isBlank()) desc += dutyType + " ";
            return SuggestionsResponseData.builder()
                    .message("No personnel found matching: " + desc.trim())
                    .suggestions(List.of("List all personnel", "Show Section A", "Show all AHC"))
                    .build();
        }
        return buildPersonnelTable(results);
    }

    private Object executeGetLeaveDetails(Map<String, Object> params) {
        String status = (String) params.getOrDefault("status", "");
        String leaveType = (String) params.getOrDefault("leave_type", "");
        String startDateStr = (String) params.getOrDefault("start_date", "");
        String endDateStr = (String) params.getOrDefault("end_date", "");

        com.policescheduler.dto.LeaveFilter filter = com.policescheduler.dto.LeaveFilter.builder().build();
        if (!status.isBlank()) filter.setStatus(status.toUpperCase());
        if (!leaveType.isBlank()) filter.setLeaveType(leaveType.toUpperCase());
        if (!startDateStr.isBlank()) {
            try { filter.setStartDate(java.time.LocalDate.parse(startDateStr)); } catch (Exception ignored) {}
        }
        if (!endDateStr.isBlank()) {
            try { filter.setEndDate(java.time.LocalDate.parse(endDateStr)); } catch (Exception ignored) {}
        }

        var leaves = leaveService.listRequests(filter, PageRequest.of(0, 100), "admin", "ADMIN");
        if (leaves.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No leave requests found matching the criteria.")
                    .suggestions(List.of("Show all leave requests", "Show pending leaves", "Show sick leaves"))
                    .build();
        }

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("name").label("Name").build(),
                TableResponseData.ColumnDef.builder().key("badgeId").label("Badge ID").build(),
                TableResponseData.ColumnDef.builder().key("leaveType").label("Type").build(),
                TableResponseData.ColumnDef.builder().key("startDate").label("From").build(),
                TableResponseData.ColumnDef.builder().key("endDate").label("To").build(),
                TableResponseData.ColumnDef.builder().key("status").label("Status").build()
        );
        List<Map<String, Object>> rows = leaves.getContent().stream()
                .map(l -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", l.getPersonnelName() != null ? l.getPersonnelName() : "—");
                    row.put("badgeId", l.getBadgeId() != null ? l.getBadgeId() : "—");
                    row.put("leaveType", l.getLeaveType());
                    row.put("startDate", l.getStartDate() != null ? l.getStartDate().toString() : "—");
                    row.put("endDate", l.getEndDate() != null ? l.getEndDate().toString() : "—");
                    row.put("status", l.getStatus());
                    return row;
                }).toList();
        return TableResponseData.builder().columns(columns).rows(rows).totalCount((int) leaves.getTotalElements()).build();
    }

    private Object executeGetTodayDuties(Map<String, Object> params) {
        String dutyTypeFilter = (String) params.getOrDefault("duty_type", "");
        String sectionFilter = (String) params.getOrDefault("section", "");

        var assignments = dutyTypeFilter.isBlank() && sectionFilter.isBlank()
                ? scheduleService.getSectionsOverview()
                : null;

        // If filtering by section
        if (!sectionFilter.isBlank()) {
            String sec = sectionFilter.toUpperCase().replace("SECTION ", "").trim();
            PersonnelFilter filter = new PersonnelFilter();
            filter.setSection(sec);
            filter.setIsActive(true);
            Page<PersonnelDto> results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
            if (results.isEmpty()) {
                return SuggestionsResponseData.builder()
                        .message("No personnel on duty in Section " + sec + " today.")
                        .suggestions(List.of("Show Section A duties", "Show platoon rotation"))
                        .build();
            }
            return buildPersonnelTable(results);
        }

        // If filtering by duty type
        if (!dutyTypeFilter.isBlank()) {
            PersonnelFilter filter = new PersonnelFilter();
            filter.setDutyType(dutyTypeFilter);
            filter.setIsActive(true);
            Page<PersonnelDto> results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
            if (results.isEmpty()) {
                // Try as search keyword
                filter = new PersonnelFilter();
                filter.setSearch(dutyTypeFilter);
                filter.setIsActive(true);
                results = personnelService.listPersonnel(filter, PageRequest.of(0, 1000));
            }
            if (results.isEmpty()) {
                return SuggestionsResponseData.builder()
                        .message("No personnel found on " + dutyTypeFilter + " duty today.")
                        .suggestions(List.of("Show guard duty", "Show check post duty", "Show all duties"))
                        .build();
            }
            return buildPersonnelTable(results);
        }

        // General overview
        return executeGetScheduleOverview();
    }

    private Object executeUpdatePersonnel(Map<String, Object> params) {
        String badgeId = (String) params.getOrDefault("badge_id", "");
        if (badgeId.isBlank()) {
            return SuggestionsResponseData.builder()
                    .message("Please provide the badge ID of the person to update.")
                    .suggestions(List.of("Search for person first", "List all personnel"))
                    .build();
        }

        String newName = (String) params.getOrDefault("person_name", "");
        String newSection = (String) params.getOrDefault("section", "");
        String newDutyType = (String) params.getOrDefault("duty_type", "");
        String newDesignation = (String) params.getOrDefault("designation", "");
        String newPhone = (String) params.getOrDefault("phone_number", "");
        String newEmail = (String) params.getOrDefault("email", "");
        String newLocation = (String) params.getOrDefault("location", "");
        String newVehicle = (String) params.getOrDefault("vehicle_number", "");
        String newPdms = (String) params.getOrDefault("pdms", "");
        String newLicenceType = (String) params.getOrDefault("licence_type", "");
        String isActiveStr = (String) params.getOrDefault("is_active", "");

        try {
            com.policescheduler.dto.UpdatePersonnelRequest request = new com.policescheduler.dto.UpdatePersonnelRequest();
            List<String> updatedFields = new ArrayList<>();

            if (!newName.isBlank()) { request.setPersonName(newName); updatedFields.add("Name → " + newName); }
            if (!newSection.isBlank()) { request.setSection(newSection.toUpperCase().trim()); updatedFields.add("Section → " + newSection.toUpperCase().trim()); }
            if (!newDutyType.isBlank()) { request.setDutyType(newDutyType); updatedFields.add("Duty Type → " + newDutyType); }
            if (!newDesignation.isBlank()) { request.setDesignation(newDesignation.toUpperCase().trim()); updatedFields.add("Designation → " + newDesignation.toUpperCase().trim()); }
            if (!newPhone.isBlank()) { request.setPhoneNumber(newPhone); updatedFields.add("Phone → " + newPhone); }
            if (!newEmail.isBlank()) { request.setEmail(newEmail); updatedFields.add("Email → " + newEmail); }
            if (!newLocation.isBlank()) { request.setLocation(newLocation); updatedFields.add("Location → " + newLocation); }
            if (!newVehicle.isBlank()) { request.setVehicleNumber(newVehicle); updatedFields.add("Vehicle → " + newVehicle); }
            if (!newPdms.isBlank()) { request.setPdms(newPdms); updatedFields.add("PDMS → " + newPdms); }
            if (!newLicenceType.isBlank()) { request.setLicenceType(newLicenceType); updatedFields.add("Licence Type → " + newLicenceType); }
            if (!isActiveStr.isBlank()) { request.setIsActive(Boolean.parseBoolean(isActiveStr)); updatedFields.add("Active → " + isActiveStr); }

            if (updatedFields.isEmpty()) {
                return SuggestionsResponseData.builder()
                        .message("No fields specified to update. Provide at least one field to change.")
                        .suggestions(List.of("Update name for " + badgeId, "Update section for " + badgeId, "Update designation for " + badgeId))
                        .build();
            }

            PersonnelDto updated = personnelService.update(badgeId, request);

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Badge ID", badgeId);
            details.put("Name", updated.getPersonName() != null ? updated.getPersonName() : "—");
            updatedFields.forEach(f -> { String[] parts = f.split(" → "); details.put(parts[0] + " (updated)", parts[1]); });

            return ConfirmationResponseData.builder()
                    .success(true)
                    .message("Successfully updated " + updated.getPersonName() + " (" + badgeId + ")")
                    .details(details)
                    .build();
        } catch (Exception e) {
            return SuggestionsResponseData.builder()
                    .message("Failed to update: " + e.getMessage())
                    .suggestions(List.of("Search by badge ID first", "List all personnel"))
                    .build();
        }
    }

    private Object executeGetStrengthSummary(Map<String, Object> params) {
        String section = (String) params.getOrDefault("section", "");

        List<Personnel> personnel;
        if (!section.isBlank()) {
            String sec = section.toUpperCase().replace("SECTION ", "").trim();
            personnel = personnelRepository.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                    .filter(p -> sec.equals(p.getSection()))
                    .toList();
        } else {
            personnel = personnelRepository.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                    .toList();
        }

        Map<String, Long> byDesignation = personnel.stream()
                .filter(p -> p.getDesignation() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getDesignation().toUpperCase().trim(),
                        java.util.stream.Collectors.counting()));

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("designation").label("Designation").build(),
                TableResponseData.ColumnDef.builder().key("count").label("Count").build()
        );

        String[] desigOrder = {"DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC"};
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        for (String d : desigOrder) {
            long count = byDesignation.getOrDefault(d, 0L);
            total += count;
            rows.add(Map.of("designation", d, "count", count));
        }
        rows.add(Map.of("designation", "TOTAL", "count", total));

        String title = section.isBlank() ? "Overall Strength" : "Section " + section.toUpperCase() + " Strength";
        return TableResponseData.builder().columns(columns).rows(rows).totalCount(rows.size())
                .meta(Map.of("title", title)).build();
    }

    private Object executeSearchDriversFiltered(Map<String, Object> params) {
        String pdmsFilter = (String) params.getOrDefault("pdms", "");
        String licenceFilter = (String) params.getOrDefault("licence_type", "");
        String vehicleFilter = (String) params.getOrDefault("vehicle", "");

        List<Personnel> drivers = personnelRepository.findMtSectionPersonnel();

        // Apply filters
        if (!pdmsFilter.isBlank()) {
            String pdmsUpper = pdmsFilter.toUpperCase();
            if (pdmsUpper.contains("NOT") || pdmsUpper.contains("PENDING")) {
                drivers = drivers.stream()
                        .filter(d -> d.getPdms() == null || d.getPdms().toUpperCase().contains("NOT") || d.getPdms().isBlank())
                        .toList();
            } else if (pdmsUpper.contains("DONE")) {
                drivers = drivers.stream()
                        .filter(d -> d.getPdms() != null && d.getPdms().toUpperCase().contains("DONE") && !d.getPdms().toUpperCase().contains("NOT"))
                        .toList();
            }
        }

        if (!licenceFilter.isBlank()) {
            String licUpper = licenceFilter.toUpperCase();
            drivers = drivers.stream()
                    .filter(d -> d.getLicenceType() != null && d.getLicenceType().toUpperCase().contains(licUpper))
                    .toList();
        }

        if (!vehicleFilter.isBlank()) {
            drivers = drivers.stream()
                    .filter(d -> d.getVehicleNumber() != null && d.getVehicleNumber().toUpperCase().contains(vehicleFilter.toUpperCase()))
                    .toList();
        }

        if (drivers.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No drivers found matching the criteria.")
                    .suggestions(List.of("Show all drivers", "Show drivers without PDMS", "Show LMV drivers"))
                    .build();
        }

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("personName").label("Name").build(),
                TableResponseData.ColumnDef.builder().key("badgeId").label("Badge ID").build(),
                TableResponseData.ColumnDef.builder().key("designation").label("Designation").build(),
                TableResponseData.ColumnDef.builder().key("vehicleNumber").label("Vehicle").build(),
                TableResponseData.ColumnDef.builder().key("pdms").label("PDMS").build(),
                TableResponseData.ColumnDef.builder().key("licenceType").label("Licence").build()
        );
        List<Map<String, Object>> rows = drivers.stream()
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("personName", p.getPersonName());
                    row.put("badgeId", p.getBadgeId());
                    row.put("designation", p.getDesignation());
                    row.put("vehicleNumber", p.getVehicleNumber() != null ? p.getVehicleNumber() : "—");
                    row.put("pdms", p.getPdms() != null ? p.getPdms() : "—");
                    row.put("licenceType", p.getLicenceType() != null ? p.getLicenceType() : "—");
                    return row;
                }).toList();
        return TableResponseData.builder().columns(columns).rows(rows).totalCount(drivers.size()).build();
    }

    private Object executeGetDutyTypes(Map<String, Object> params) {
        String section = (String) params.getOrDefault("section", null);

        List<DutyType> dutyTypes;
        if (section != null && !section.isEmpty()) {
            dutyTypes = dutyTypeRepository.findBySectionOrderBySortOrderAsc(section.toUpperCase().trim());
        } else {
            dutyTypes = dutyTypeRepository.findAllByOrderBySortOrderAsc();
        }

        if (dutyTypes.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No duty types found" + (section != null ? " for Section " + section : "") + ".")
                    .suggestions(List.of("Show all duty types", "Show Section A duty types", "Show Section B duty types"))
                    .build();
        }

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("name").label("Duty Type").build(),
                TableResponseData.ColumnDef.builder().key("section").label("Section").build(),
                TableResponseData.ColumnDef.builder().key("has_location").label("Has Location").build()
        );
        List<Map<String, Object>> rows = dutyTypes.stream().map(dt -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", dt.getName());
            row.put("section", dt.getSection());
            row.put("has_location", dt.getLatitude() != null ? "Yes" : "No");
            return row;
        }).collect(Collectors.toList());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("description", "Here are the duty types" + (section != null ? " for Section " + section.toUpperCase() : "") + ":");

        return TableResponseData.builder()
                .columns(columns)
                .rows(rows)
                .totalCount(dutyTypes.size())
                .meta(meta)
                .build();
    }

    private TableResponseData buildPersonnelTable(Page<PersonnelDto> results) {
        // Build section code → name map for display
        Map<String, String> sectionNameMap = new HashMap<>();
        sectionRepository.findByIsActiveTrueOrderBySortOrder().forEach(s ->
                sectionNameMap.put(s.getCode(), s.getName()));

        List<TableResponseData.ColumnDef> columns = List.of(
                TableResponseData.ColumnDef.builder().key("personName").label("Name").build(),
                TableResponseData.ColumnDef.builder().key("badgeId").label("Badge ID").build(),
                TableResponseData.ColumnDef.builder().key("designation").label("Designation").build(),
                TableResponseData.ColumnDef.builder().key("section").label("Section").build(),
                TableResponseData.ColumnDef.builder().key("status").label("Status").build()
        );
        List<Map<String, Object>> rows = results.getContent().stream()
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("personName", p.getPersonName());
                    row.put("badgeId", p.getBadgeId());
                    row.put("designation", p.getDesignation());
                    // Show section name (e.g., "Guard I") instead of just code ("C")
                    String sectionCode = p.getSection();
                    String sectionDisplay = sectionNameMap.getOrDefault(sectionCode, sectionCode);
                    row.put("section", sectionDisplay);
                    row.put("status", Boolean.TRUE.equals(p.getIsActive()) ? "Active" : "Inactive");
                    return row;
                })
                .toList();
        return TableResponseData.builder()
                .columns(columns)
                .rows(rows)
                .totalCount((int) results.getTotalElements())
                .build();
    }

    private Object executeAddDutyType(Map<String, Object> params) {
        String name = (String) params.getOrDefault("name", "");
        String section = (String) params.getOrDefault("section", "");

        if (name.isBlank() || section.isBlank()) {
            return SuggestionsResponseData.builder()
                    .message("To add a duty type, provide both name and section.")
                    .suggestions(List.of("Add duty type PATROL in Section A", "Add duty type TRAFFIC in Section C"))
                    .build();
        }

        // Check if already exists
        String normalizedName = name.toUpperCase().trim();
        String normalizedSection = section.toUpperCase().trim();
        List<DutyType> existing = dutyTypeRepository.findBySectionOrderBySortOrderAsc(normalizedSection);
        boolean alreadyExists = existing.stream().anyMatch(dt -> dt.getName().equalsIgnoreCase(normalizedName));
        if (alreadyExists) {
            return SuggestionsResponseData.builder()
                    .message("Duty type '" + normalizedName + "' already exists in Section " + normalizedSection + ".")
                    .suggestions(List.of("Show duty types in Section " + normalizedSection, "Update duty type " + normalizedName))
                    .build();
        }

        DutyType dutyType = new DutyType();
        dutyType.setName(normalizedName);
        dutyType.setSection(normalizedSection);
        dutyType.setSortOrder(existing.size() + 1);

        String latStr = (String) params.getOrDefault("latitude", "");
        String lonStr = (String) params.getOrDefault("longitude", "");
        String radiusStr = (String) params.getOrDefault("radius_meters", "");
        if (!latStr.isBlank()) dutyType.setLatitude(Double.parseDouble(latStr));
        if (!lonStr.isBlank()) dutyType.setLongitude(Double.parseDouble(lonStr));
        if (!radiusStr.isBlank()) dutyType.setRadiusMeters(Integer.parseInt(radiusStr));

        DutyType saved = dutyTypeRepository.save(dutyType);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Name", saved.getName());
        details.put("Section", saved.getSection());
        details.put("Sort Order", String.valueOf(saved.getSortOrder()));
        if (saved.getLatitude() != null) details.put("Location", saved.getLatitude() + ", " + saved.getLongitude());

        return ConfirmationResponseData.builder()
                .success(true)
                .message("Duty type '" + saved.getName() + "' created successfully in Section " + saved.getSection() + ".")
                .details(details)
                .build();
    }

    private Object executeUpdateDutyType(Map<String, Object> params) {
        String name = (String) params.getOrDefault("name", "");
        if (name.isBlank()) {
            return SuggestionsResponseData.builder()
                    .message("Please provide the name of the duty type to update.")
                    .suggestions(List.of("Show all duty types", "Update duty type GUARD"))
                    .build();
        }

        // Find duty type by name (case-insensitive)
        List<DutyType> allTypes = dutyTypeRepository.findAllByOrderBySortOrderAsc();
        DutyType dutyType = allTypes.stream()
                .filter(dt -> dt.getName().equalsIgnoreCase(name.trim()))
                .findFirst().orElse(null);

        if (dutyType == null) {
            return SuggestionsResponseData.builder()
                    .message("Duty type '" + name + "' not found.")
                    .suggestions(List.of("Show all duty types", "Add duty type " + name))
                    .build();
        }

        String newName = (String) params.getOrDefault("new_name", "");
        String newSection = (String) params.getOrDefault("section", "");
        String latStr = (String) params.getOrDefault("latitude", "");
        String lonStr = (String) params.getOrDefault("longitude", "");
        List<String> changes = new ArrayList<>();

        if (!newName.isBlank()) { dutyType.setName(newName.toUpperCase().trim()); changes.add("Name → " + newName.toUpperCase().trim()); }
        if (!newSection.isBlank()) { dutyType.setSection(newSection.toUpperCase().trim()); changes.add("Section → " + newSection.toUpperCase().trim()); }
        if (!latStr.isBlank()) { dutyType.setLatitude(Double.parseDouble(latStr)); changes.add("Latitude → " + latStr); }
        if (!lonStr.isBlank()) { dutyType.setLongitude(Double.parseDouble(lonStr)); changes.add("Longitude → " + lonStr); }

        if (changes.isEmpty()) {
            return SuggestionsResponseData.builder()
                    .message("No changes specified. What would you like to update for '" + name + "'?")
                    .suggestions(List.of("Change name", "Change section", "Update location"))
                    .build();
        }

        dutyTypeRepository.save(dutyType);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Duty Type", dutyType.getName());
        details.put("Section", dutyType.getSection());
        changes.forEach(c -> { String[] parts = c.split(" → "); details.put(parts[0] + " (updated)", parts[1]); });

        return ConfirmationResponseData.builder()
                .success(true)
                .message("Duty type '" + dutyType.getName() + "' updated successfully.")
                .details(details)
                .build();
    }

    private Object executeDeleteDutyType(Map<String, Object> params) {
        String name = (String) params.getOrDefault("name", "");
        if (name.isBlank()) {
            return SuggestionsResponseData.builder()
                    .message("Please provide the name of the duty type to delete.")
                    .suggestions(List.of("Show all duty types"))
                    .build();
        }

        List<DutyType> allTypes = dutyTypeRepository.findAllByOrderBySortOrderAsc();
        DutyType dutyType = allTypes.stream()
                .filter(dt -> dt.getName().equalsIgnoreCase(name.trim()))
                .findFirst().orElse(null);

        if (dutyType == null) {
            return SuggestionsResponseData.builder()
                    .message("Duty type '" + name + "' not found.")
                    .suggestions(List.of("Show all duty types"))
                    .build();
        }

        String deletedName = dutyType.getName();
        String deletedSection = dutyType.getSection();
        dutyTypeRepository.delete(dutyType);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Name", deletedName);
        details.put("Section", deletedSection);

        return ConfirmationResponseData.builder()
                .success(true)
                .message("Duty type '" + deletedName + "' deleted from Section " + deletedSection + ".")
                .details(details)
                .build();
    }
}
