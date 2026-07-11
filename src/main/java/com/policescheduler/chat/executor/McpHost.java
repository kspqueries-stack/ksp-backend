package com.policescheduler.chat.executor;

import com.policescheduler.chat.model.ActionPlan;
import com.policescheduler.chat.model.ProcessingContext;
import com.policescheduler.chat.model.ToolExecutionResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpHost {

    private static final Logger log = LoggerFactory.getLogger(McpHost.class);

    private final List<McpServer> servers;
    private final Map<String, McpServer> toolToServer = new ConcurrentHashMap<>();
    private final Map<String, McpToolDefinition> toolDefinitions = new ConcurrentHashMap<>();

    public McpHost(List<McpServer> servers) {
        this.servers = servers;
    }

    @PostConstruct
    public void indexTools() {
        for (McpServer server : servers) {
            for (McpToolDefinition tool : server.listTools()) {
                toolToServer.put(tool.toolName(), server);
                toolDefinitions.put(tool.toolName(), tool);
                log.info("Registered MCP tool: {} from server: {}", tool.toolName(), server.getServerId());
            }
        }
        log.info("McpHost indexed {} tools from {} servers", toolDefinitions.size(), servers.size());
    }

    public ToolExecutionResult dispatch(ActionPlan plan, ProcessingContext context) {
        String toolName = plan.toolName();
        if (toolName == null) {
            return ToolExecutionResult.failure(plan.intent(), "NO_TOOL", "No tool specified for intent: " + plan.intent(), 0);
        }

        McpServer server = toolToServer.get(toolName);
        if (server == null) {
            return ToolExecutionResult.failure(toolName, "TOOL_NOT_FOUND", "Tool not registered: " + toolName, 0);
        }

        long start = System.currentTimeMillis();
        try {
            McpToolResult result = server.executeTool(toolName, plan.parameters(), context);
            long latency = System.currentTimeMillis() - start;
            if (result.success()) {
                return ToolExecutionResult.success(toolName, result.data(), latency);
            } else {
                return ToolExecutionResult.failure(toolName, result.errorType(), result.errorMessage(), latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("Tool execution failed: {} - {}", toolName, e.getMessage(), e);
            return ToolExecutionResult.failure(toolName, "EXECUTION_ERROR", e.getMessage(), latency);
        }
    }

    public List<ToolExecutionResult> dispatchAll(List<ActionPlan> plans, ProcessingContext context) {
        List<ToolExecutionResult> results = new ArrayList<>();
        for (ActionPlan plan : plans) {
            results.add(dispatch(plan, context));
        }
        return results;
    }

    public boolean isToolRegistered(String toolName) {
        return toolToServer.containsKey(toolName);
    }

    public Optional<McpToolDefinition> getToolDefinition(String toolName) {
        return Optional.ofNullable(toolDefinitions.get(toolName));
    }
}
