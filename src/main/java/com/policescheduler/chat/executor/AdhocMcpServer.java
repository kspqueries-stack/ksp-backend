package com.policescheduler.chat.executor;

import com.policescheduler.chat.model.ProcessingContext;
import com.policescheduler.dto.AdhocAssigneeInfo;
import com.policescheduler.dto.AdhocDutyResponse;
import com.policescheduler.dto.CreateAdhocDutyRequest;
import com.policescheduler.dto.chat.FormResponseData;
import com.policescheduler.dto.chat.TableResponseData;
import com.policescheduler.service.AdhocDutyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP server providing chat tools for adhoc/special/bandobast duty management.
 * Registered automatically via Spring DI into McpHost.
 */
@Service
public class AdhocMcpServer implements McpServer {

    private static final Logger log = LoggerFactory.getLogger(AdhocMcpServer.class);

    private final AdhocDutyService adhocDutyService;

    public AdhocMcpServer(AdhocDutyService adhocDutyService) {
        this.adhocDutyService = adhocDutyService;
    }

    @Override
    public String getServerId() {
        return "adhoc-duty";
    }

    @Override
    public List<McpToolDefinition> listTools() {
        return List.of(
            new McpToolDefinition("create_adhoc_duty",
                "Create adhoc/special/bandobast duty. Supports auto-pick or manual personnel selection via personnel_ids.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "duty_name", Map.of("type", "string", "description", "Name of adhoc duty"),
                        "date", Map.of("type", "string", "description", "Date in yyyy-MM-dd format"),
                        "start_time", Map.of("type", "string", "description", "Start time in HH:mm format"),
                        "end_time", Map.of("type", "string", "description", "End time in HH:mm format"),
                        "required_count", Map.of("type", "string", "description", "Number of personnel needed"),
                        "location", Map.of("type", "string", "description", "Duty location"),
                        "pick_from", Map.of("type", "string", "description", "ALL_PLATOONS or SPECIFIC_PLATOONS"),
                        "personnel_ids", Map.of("type", "string", "description", "Optional comma-separated personnel IDs for manual selection. When provided, skips auto-pick.")
                    ),
                    "required", List.of("duty_name", "date", "start_time", "end_time", "required_count")
                )),
            new McpToolDefinition("list_adhoc_duties",
                "List all adhoc/special duties. Filter by date or status.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "date", Map.of("type", "string", "description", "Filter by date yyyy-MM-dd"),
                        "status", Map.of("type", "string", "description", "Filter: ACTIVE, COMPLETED, CANCELLED")
                    )
                )),
            new McpToolDefinition("get_adhoc_duty_details",
                "Get full details of a specific adhoc duty including assigned personnel",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "adhoc_duty_id", Map.of("type", "string", "description", "Adhoc duty ID")
                    ),
                    "required", List.of("adhoc_duty_id")
                )),
            new McpToolDefinition("cancel_adhoc_duty",
                "Cancel an adhoc duty and release all assigned personnel back to normal duty",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "adhoc_duty_id", Map.of("type", "string", "description", "Adhoc duty ID to cancel")
                    ),
                    "required", List.of("adhoc_duty_id")
                )),
            new McpToolDefinition("preview_adhoc_personnel",
                "Preview available personnel for adhoc duty assignment. Shows who can be picked based on date, time, and count.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "date", Map.of("type", "string", "description", "Date in yyyy-MM-dd format"),
                        "start_time", Map.of("type", "string", "description", "Start time in HH:mm format"),
                        "end_time", Map.of("type", "string", "description", "End time in HH:mm format"),
                        "count", Map.of("type", "string", "description", "Number of personnel to preview"),
                        "pick_from", Map.of("type", "string", "description", "ALL_PLATOONS or SPECIFIC_PLATOONS")
                    ),
                    "required", List.of("date", "start_time", "end_time", "count")
                )),
            new McpToolDefinition("search_adhoc_personnel",
                "Search available personnel by name or badge ID for manual addition to adhoc duty. Filters out those on leave or already assigned.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Search text - name or badge ID"),
                        "date", Map.of("type", "string", "description", "Duty date in yyyy-MM-dd format"),
                        "start_time", Map.of("type", "string", "description", "Start time in HH:mm format"),
                        "end_time", Map.of("type", "string", "description", "End time in HH:mm format")
                    ),
                    "required", List.of("query", "date", "start_time", "end_time")
                ))
        );
    }

    @Override
    public McpToolResult executeTool(String toolName, Map<String, Object> parameters, ProcessingContext context) {
        log.info("AdhocMcpServer executing tool: {} with params: {}", toolName, parameters);
        try {
            return switch (toolName) {
                case "create_adhoc_duty" -> executeCreateAdhocDuty(parameters);
                case "list_adhoc_duties" -> executeListAdhocDuties(parameters);
                case "get_adhoc_duty_details" -> executeGetDetails(parameters);
                case "cancel_adhoc_duty" -> executeCancelAdhocDuty(parameters);
                case "preview_adhoc_personnel" -> executePreviewAdhocPersonnel(parameters);
                case "search_adhoc_personnel" -> executeSearchAdhocPersonnel(parameters);
                default -> McpToolResult.failure("UNKNOWN_TOOL", "Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("Tool execution error for {}: {}", toolName, e.getMessage(), e);
            return McpToolResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }

    private McpToolResult executeCreateAdhocDuty(Map<String, Object> params) {
        String dutyName = params != null ? (String) params.get("duty_name") : null;
        String dateStr = params != null ? (String) params.get("date") : null;
        String startTimeStr = params != null ? (String) params.get("start_time") : null;
        String endTimeStr = params != null ? (String) params.get("end_time") : null;
        String countStr = params != null ? (String) params.get("required_count") : null;
        String location = params != null ? (String) params.get("location") : null;
        String pickFrom = params != null ? (String) params.get("pick_from") : null;
        String personnelIdsStr = params != null ? (String) params.get("personnel_ids") : null;

        // If ALL required parameters are present, create directly
        if (dutyName != null && !dutyName.isBlank()
            && dateStr != null && !dateStr.isBlank()
            && startTimeStr != null && !startTimeStr.isBlank()
            && endTimeStr != null && !endTimeStr.isBlank()
            && countStr != null && !countStr.isBlank()) {

            LocalDate date = LocalDate.parse(dateStr);
            LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime endTime = LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            int requiredCount = Integer.parseInt(countStr);

            // Parse optional personnel_ids (comma-separated)
            List<Long> personnelIds = parsePersonnelIds(personnelIdsStr);

            CreateAdhocDutyRequest request = new CreateAdhocDutyRequest(
                dutyName, date, startTime, endTime, requiredCount, location, pickFrom, null, personnelIds
            );

            AdhocDutyResponse response = adhocDutyService.createAdhocDuty(request);
            return McpToolResult.success(formatDutyResponse(response));
        }

        // Parameters incomplete — show a form with pre-filled values from what user provided
        List<FormResponseData.FieldDef> fields = new ArrayList<>();

        fields.add(FormResponseData.FieldDef.builder()
                .name("duty_name").label("Duty Name").type("text")
                .required(true).value(dutyName != null ? dutyName : "").build());
        fields.add(FormResponseData.FieldDef.builder()
                .name("date").label("Date").type("date")
                .required(true).value(dateStr != null ? dateStr : LocalDate.now().toString()).build());
        fields.add(FormResponseData.FieldDef.builder()
                .name("start_time").label("Start Time").type("time")
                .required(true).value(startTimeStr != null ? startTimeStr : "09:00").build());
        fields.add(FormResponseData.FieldDef.builder()
                .name("end_time").label("End Time").type("time")
                .required(true).value(endTimeStr != null ? endTimeStr : "17:00").build());
        fields.add(FormResponseData.FieldDef.builder()
                .name("required_count").label("Required Personnel Count").type("number")
                .required(true).value(countStr != null ? countStr : "5").build());
        fields.add(FormResponseData.FieldDef.builder()
                .name("location").label("Location").type("text")
                .required(false).value(location != null ? location : "").build());
        fields.add(FormResponseData.FieldDef.builder()
                .name("pick_from").label("Pick From").type("select")
                .required(false).value(pickFrom != null ? pickFrom : "ALL_PLATOONS")
                .options(List.of("ALL_PLATOONS", "SPECIFIC_PLATOONS")).build());
        fields.add(FormResponseData.FieldDef.builder()
                .name("personnel_ids").label("Personnel IDs (comma-separated, leave empty for auto-pick)").type("text")
                .required(false).value(personnelIdsStr != null ? personnelIdsStr : "").build());

        FormResponseData form = FormResponseData.builder()
                .formId("create-adhoc-duty-" + System.currentTimeMillis())
                .title("Create Adhoc Duty Request")
                .submitAction("create_adhoc_duty")
                .submitLabel("Create & Auto-Pick Personnel")
                .fields(fields)
                .build();

        return McpToolResult.success(form);
    }

    private McpToolResult executeListAdhocDuties(Map<String, Object> params) {
        String dateStr = params != null ? (String) params.get("date") : null;
        String status = params != null ? (String) params.get("status") : null;

        LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : null;

        List<AdhocDutyResponse> duties = adhocDutyService.listAdhocDuties(date, status);

        if (duties.isEmpty()) {
            return McpToolResult.success("No adhoc duties found.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(duties.size()).append(" adhoc duties:\n\n");
        for (AdhocDutyResponse d : duties) {
            sb.append("ID: ").append(d.id())
              .append(" | ").append(d.dutyName())
              .append(" | Date: ").append(d.date())
              .append(" | Time: ").append(d.startTime()).append("-").append(d.endTime())
              .append(" | Status: ").append(d.status())
              .append(" | Personnel: ").append(d.actualCount()).append("/").append(d.requiredCount())
              .append("\n");
        }
        return McpToolResult.success(sb.toString());
    }

    private McpToolResult executeGetDetails(Map<String, Object> params) {
        String idStr = (String) params.get("adhoc_duty_id");
        if (idStr == null) {
            return McpToolResult.failure("MISSING_PARAMS", "Required: adhoc_duty_id");
        }
        Long id = Long.parseLong(idStr);
        AdhocDutyResponse response = adhocDutyService.getAdhocDutyDetails(id);
        return McpToolResult.success(formatDutyResponse(response));
    }

    private McpToolResult executeCancelAdhocDuty(Map<String, Object> params) {
        String idStr = (String) params.get("adhoc_duty_id");
        if (idStr == null) {
            return McpToolResult.failure("MISSING_PARAMS", "Required: adhoc_duty_id");
        }
        Long id = Long.parseLong(idStr);
        AdhocDutyResponse response = adhocDutyService.cancelAdhocDuty(id);
        return McpToolResult.success("Adhoc duty '" + response.dutyName() + "' cancelled. " +
            (response.assignees() != null ? response.assignees().size() : 0) + " personnel released.");
    }

    private McpToolResult executePreviewAdhocPersonnel(Map<String, Object> params) {
        String dateStr = params != null ? (String) params.get("date") : null;
        String startTimeStr = params != null ? (String) params.get("start_time") : null;
        String endTimeStr = params != null ? (String) params.get("end_time") : null;
        String countStr = params != null ? (String) params.get("count") : null;
        String pickFrom = params != null ? (String) params.get("pick_from") : null;

        if (dateStr == null || startTimeStr == null || endTimeStr == null || countStr == null) {
            return McpToolResult.failure("MISSING_PARAMS", "Required: date, start_time, end_time, count");
        }

        LocalDate date = LocalDate.parse(dateStr);
        LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime = LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
        int count = Integer.parseInt(countStr);

        List<AdhocAssigneeInfo> personnel = adhocDutyService.previewAvailablePersonnel(
            date, startTime, endTime, count,
            pickFrom != null ? pickFrom : "ALL_PLATOONS",
            null
        );

        if (personnel.isEmpty()) {
            return McpToolResult.success("No available personnel found for the given criteria.");
        }

        List<TableResponseData.ColumnDef> columns = List.of(
            TableResponseData.ColumnDef.builder().key("personId").label("ID").build(),
            TableResponseData.ColumnDef.builder().key("personName").label("Name").build(),
            TableResponseData.ColumnDef.builder().key("badgeId").label("Badge").build(),
            TableResponseData.ColumnDef.builder().key("section").label("Section").build(),
            TableResponseData.ColumnDef.builder().key("originalDutyName").label("Current Duty").build(),
            TableResponseData.ColumnDef.builder().key("originalShift").label("Shift").build(),
            TableResponseData.ColumnDef.builder().key("disruptionScore").label("Disruption (min)").build()
        );

        List<Map<String, Object>> rows = personnel.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("personId", p.personId());
            row.put("personName", p.personName());
            row.put("badgeId", p.badgeId());
            row.put("section", p.section());
            row.put("originalDutyName", p.originalDutyName() != null ? p.originalDutyName() : "—");
            row.put("originalShift", p.originalShift() != null ? p.originalShift() : "—");
            row.put("disruptionScore", p.disruptionScore() != null ? p.disruptionScore() : 0);
            return row;
        }).collect(Collectors.toList());

        return McpToolResult.success(TableResponseData.builder()
            .columns(columns)
            .rows(rows)
            .totalCount(personnel.size())
            .meta(Map.of("description", "Available personnel for adhoc duty. Use their IDs with create_adhoc_duty personnel_ids parameter."))
            .build());
    }

    private McpToolResult executeSearchAdhocPersonnel(Map<String, Object> params) {
        String query = params != null ? (String) params.get("query") : null;
        String dateStr = params != null ? (String) params.get("date") : null;
        String startTimeStr = params != null ? (String) params.get("start_time") : null;
        String endTimeStr = params != null ? (String) params.get("end_time") : null;

        if (query == null || dateStr == null || startTimeStr == null || endTimeStr == null) {
            return McpToolResult.failure("MISSING_PARAMS", "Required: query, date, start_time, end_time");
        }

        LocalDate date = LocalDate.parse(dateStr);
        LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime = LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm"));

        List<AdhocAssigneeInfo> results = adhocDutyService.searchAvailablePersonnel(query, date, startTime, endTime);

        if (results.isEmpty()) {
            return McpToolResult.success("No available personnel found matching '" + query + "'.");
        }

        List<TableResponseData.ColumnDef> columns = List.of(
            TableResponseData.ColumnDef.builder().key("personId").label("ID").build(),
            TableResponseData.ColumnDef.builder().key("personName").label("Name").build(),
            TableResponseData.ColumnDef.builder().key("badgeId").label("Badge").build(),
            TableResponseData.ColumnDef.builder().key("designation").label("Designation").build(),
            TableResponseData.ColumnDef.builder().key("section").label("Section").build(),
            TableResponseData.ColumnDef.builder().key("originalDutyName").label("Current Duty").build(),
            TableResponseData.ColumnDef.builder().key("status").label("Status").build()
        );

        List<Map<String, Object>> rows = results.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("personId", p.personId());
            row.put("personName", p.personName());
            row.put("badgeId", p.badgeId());
            row.put("designation", p.designation() != null ? p.designation() : "—");
            row.put("section", p.section());
            row.put("originalDutyName", p.originalDutyName() != null ? p.originalDutyName() : "—");
            row.put("status", p.status());
            return row;
        }).collect(Collectors.toList());

        return McpToolResult.success(TableResponseData.builder()
            .columns(columns)
            .rows(rows)
            .totalCount(results.size())
            .meta(Map.of("description", "Search results for '" + query + "'. Use personnel IDs with create_adhoc_duty personnel_ids parameter."))
            .build());
    }

    /**
     * Parse a comma-separated string of personnel IDs into a List<Long>.
     * Returns null if input is null or blank (triggers auto-pick behavior).
     */
    private List<Long> parsePersonnelIds(String personnelIdsStr) {
        if (personnelIdsStr == null || personnelIdsStr.isBlank()) {
            return null;
        }
        return Arrays.stream(personnelIdsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .collect(Collectors.toList());
    }

    private String formatDutyResponse(AdhocDutyResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Adhoc Duty #").append(response.id()).append("\n");
        sb.append("Name: ").append(response.dutyName()).append("\n");
        sb.append("Date: ").append(response.date()).append("\n");
        sb.append("Time: ").append(response.startTime()).append(" - ").append(response.endTime()).append("\n");
        if (response.location() != null) sb.append("Location: ").append(response.location()).append("\n");
        sb.append("Status: ").append(response.status()).append("\n");
        sb.append("Personnel: ").append(response.actualCount()).append("/").append(response.requiredCount()).append("\n");

        if (response.assignees() != null && !response.assignees().isEmpty()) {
            sb.append("\nAssigned Personnel:\n");
            for (AdhocAssigneeInfo a : response.assignees()) {
                sb.append("  - ").append(a.personName())
                  .append(" (").append(a.badgeId()).append(")")
                  .append(" | Section: ").append(a.section())
                  .append(" | Original: ").append(a.originalDutyName()).append(" - ").append(a.originalShift())
                  .append(" | Disruption: ").append(a.disruptionScore()).append(" min\n");
            }
        }
        return sb.toString();
    }
}
