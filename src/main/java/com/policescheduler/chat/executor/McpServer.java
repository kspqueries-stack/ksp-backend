package com.policescheduler.chat.executor;

import com.policescheduler.chat.model.ProcessingContext;

import java.util.List;
import java.util.Map;

public interface McpServer {

    String getServerId();

    List<McpToolDefinition> listTools();

    McpToolResult executeTool(String toolName, Map<String, Object> parameters, ProcessingContext context);
}
