package com.policescheduler.chat.model;

public record ToolExecutionResult(
    String toolName,
    boolean success,
    Object data,
    String errorType,
    String errorMessage,
    long latencyMs
) {
    public static ToolExecutionResult success(String toolName, Object data, long latencyMs) {
        return new ToolExecutionResult(toolName, true, data, null, null, latencyMs);
    }

    public static ToolExecutionResult failure(String toolName, String errorType, String errorMessage, long latencyMs) {
        return new ToolExecutionResult(toolName, false, null, errorType, errorMessage, latencyMs);
    }
}
