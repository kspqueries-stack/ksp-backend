package com.policescheduler.chat.executor;

import com.policescheduler.chat.model.ProcessingContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportMcpServer implements McpServer {

    @Override
    public String getServerId() {
        return "report";
    }

    @Override
    public List<McpToolDefinition> listTools() {
        return List.of(
            new McpToolDefinition("generate_platoon_chart_pdf", "Generate platoon rotation chart PDF report", Map.of()),
            new McpToolDefinition("generate_form168_pdf", "Generate daily statement Form 168 report", Map.of()),
            new McpToolDefinition("generate_section_a_pdf", "Generate Section A duty report", Map.of()),
            new McpToolDefinition("generate_section_b_pdf", "Generate Section B duty report", Map.of()),
            new McpToolDefinition("generate_section_ab_pdf", "Generate combined Section A+B report", Map.of()),
            new McpToolDefinition("generate_mt_section_pdf", "Generate MT Section drivers report", Map.of()),
            new McpToolDefinition("generate_personnel_list_pdf", "Generate personnel list report", Map.of()),
            new McpToolDefinition("generate_leave_statement_pdf", "Generate leave statement report", Map.of())
        );
    }

    @Override
    public McpToolResult executeTool(String toolName, Map<String, Object> parameters, ProcessingContext context) {
        String reportName = switch (toolName) {
            case "generate_platoon_chart_pdf" -> "Platoon Chart C - Rotational Duties";
            case "generate_form168_pdf" -> "Daily Statement (Form 168)";
            case "generate_section_a_pdf" -> "Platoon Chart A - Static Duties";
            case "generate_section_b_pdf" -> "Platoon Chart B - Semi-Static";
            case "generate_section_ab_pdf" -> "Combined Section A & B Report";
            case "generate_mt_section_pdf" -> "MT Section (Drivers) Report";
            case "generate_personnel_list_pdf" -> "Personnel List Report";
            case "generate_leave_statement_pdf" -> "Leave Statement Report";
            default -> "Report";
        };

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", "The " + reportName + " can be downloaded from the Reports page.");
        result.put("action", "Navigate to Reports page to download this report in PDF or Excel format.");
        result.put("report_name", reportName);
        result.put("download_available", true);

        return McpToolResult.success(result);
    }
}
