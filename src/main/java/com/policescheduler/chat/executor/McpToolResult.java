package com.policescheduler.chat.executor;

public record McpToolResult(
    boolean success,
    Object data,
    String errorType,
    String errorMessage
) {
    public static McpToolResult success(Object data) {
        return new McpToolResult(true, data, null, null);
    }

    public static McpToolResult failure(String errorType, String errorMessage) {
        return new McpToolResult(false, null, errorType, errorMessage);
    }
}
