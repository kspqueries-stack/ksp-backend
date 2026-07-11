package com.policescheduler.chat.response;

import com.policescheduler.chat.model.ToolExecutionResult;
import com.policescheduler.dto.ChatResponse;
import com.policescheduler.dto.chat.ConfirmationResponseData;
import com.policescheduler.dto.chat.FormResponseData;
import com.policescheduler.dto.chat.SuggestionsResponseData;
import com.policescheduler.dto.chat.TableResponseData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);

    public ChatResponse format(ToolExecutionResult result) {
        if (result == null) {
            return ChatResponse.builder()
                    .response("No result available.")
                    .responseType("text")
                    .build();
        }

        if (!result.success()) {
            return ChatResponse.builder()
                    .response("Error: " + result.errorMessage())
                    .responseType("text")
                    .build();
        }

        Object data = result.data();
        if (data == null) {
            return ChatResponse.builder()
                    .response("Action completed.")
                    .responseType("text")
                    .build();
        }

        if (data instanceof TableResponseData tableData) {
            String description = getTableDescription(result.toolName(), tableData);
            return ChatResponse.builder()
                    .response(description)
                    .responseType("table")
                    .data(tableData)
                    .build();
        }

        if (data instanceof FormResponseData formData) {
            return ChatResponse.builder()
                    .response("Please review and complete the form below.")
                    .responseType("form")
                    .data(formData)
                    .build();
        }

        if (data instanceof ConfirmationResponseData confirmData) {
            return ChatResponse.builder()
                    .response(confirmData.getMessage())
                    .responseType("confirmation")
                    .data(confirmData)
                    .build();
        }

        if (data instanceof SuggestionsResponseData suggestionsData) {
            return ChatResponse.builder()
                    .response(suggestionsData.getMessage())
                    .responseType("suggestions")
                    .data(suggestionsData)
                    .build();
        }

        // Handle Map results (from ReportMcpServer and other tools returning raw Maps)
        if (data instanceof Map<?, ?> mapData) {
            Object description = mapData.get("description");
            if (description != null) {
                return ChatResponse.builder()
                        .response(description.toString())
                        .responseType("text")
                        .data(data)
                        .build();
            }
            Object message = mapData.get("message");
            if (message != null) {
                return ChatResponse.builder()
                        .response(message.toString())
                        .responseType("text")
                        .data(data)
                        .build();
            }
        }

        // Fallback: treat as text
        return ChatResponse.builder()
                .response(data.toString())
                .responseType("text")
                .build();
    }

    /**
     * Generate a contextual description for table responses based on tool name and table metadata.
     */
    private String getTableDescription(String toolName, TableResponseData tableData) {
        // First check if the table has a meta description
        if (tableData.getMeta() != null) {
            Object metaDesc = tableData.getMeta().get("description");
            if (metaDesc != null) {
                return metaDesc.toString();
            }
            Object metaTitle = tableData.getMeta().get("title");
            if (metaTitle != null) {
                return metaTitle.toString();
            }
        }

        // Contextual descriptions based on tool name
        if (toolName == null) {
            return "Found " + tableData.getTotalCount() + " record"
                    + (tableData.getTotalCount() != 1 ? "s" : "") + ".";
        }

        return switch (toolName) {
            case "search_personnel" -> "Here are the matching personnel records (" + tableData.getTotalCount() + " found). The table shows name, badge ID, designation, section, and status for each person.";
            case "search_by_duty_type" -> "Here are the personnel assigned to this duty type (" + tableData.getTotalCount() + " found). You can search for specific people or filter by section.";
            case "search_by_designation" -> "Here are the personnel with this designation (" + tableData.getTotalCount() + " found). Each row shows their name, badge ID, section, and current status.";
            case "list_section_personnel" -> "Here are the personnel in this section (" + tableData.getTotalCount() + " total). The list includes all active members with their duty assignments.";
            case "get_person_by_badge" -> "Here are the complete details for the requested person:";
            case "get_schedule_overview" -> "Here is the current schedule overview showing duty assignments across all sections. Section A handles fixed/essential duties, Section B handles office/support, and Section C handles rotational platoon duties.";
            case "get_leave_count" -> "Here is the leave request summary showing current status breakdown. You can ask for detailed leave list or filter by type.";
            case "get_leave_details" -> "Here are the leave requests (" + tableData.getTotalCount() + " found). The table shows each request with personnel name, leave type, dates, and approval status.";
            case "get_platoon_rotation" -> "Here is the current platoon rotation showing which platoon is assigned to which duty type. Rotations happen on a fixed cycle.";
            case "get_today_duties" -> "Here are today's duty assignments. This shows who is currently on which duty.";
            case "get_strength_summary" -> "Here is the strength summary showing personnel count by designation. This gives you a quick overview of available manpower.";
            case "search_drivers" -> "Here are the drivers matching your criteria (" + tableData.getTotalCount() + " found). The table includes vehicle details, PDMS status, and licence information.";
            case "get_duty_types" -> "Here are all the duty types/posts (" + tableData.getTotalCount() + " total). Each duty type belongs to a specific section and may have a geo-location assigned.";
            case "filter_personnel" -> "Here are the personnel matching your filters (" + tableData.getTotalCount() + " found). You can further refine by adding more filters.";
            default -> "Found " + tableData.getTotalCount() + " record"
                    + (tableData.getTotalCount() != 1 ? "s" : "") + ". Here are the details:";
        };
    }
}
