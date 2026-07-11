package com.policescheduler.chat.executor;

import java.util.Map;

public record McpToolDefinition(
    String toolName,
    String description,
    Map<String, Object> parameterSchema
) {}
