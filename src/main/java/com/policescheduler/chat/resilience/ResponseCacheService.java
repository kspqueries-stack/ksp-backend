package com.policescheduler.chat.resilience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.policescheduler.chat.model.ActionPlan;
import com.policescheduler.chat.model.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

@Service
public class ResponseCacheService {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheService.class);

    private static final Set<String> WRITE_TOOLS = Set.of(
        "add_person", "create_leave", "create_cycle", "update_cycle", "delete_cycle",
        "reassign_duty", "auto_reassign_duty", "add_duty_type", "update_duty_type",
        "delete_duty_type", "update_personnel", "create_adhoc_duty", "cancel_adhoc_duty"
    );

    // Mapping of write tools to read tools they invalidate
    private static final java.util.Map<String, Set<String>> INVALIDATION_MAP = java.util.Map.ofEntries(
        java.util.Map.entry("add_person", Set.of("search_personnel", "search_by_duty_type", "search_by_designation",
                "list_section_personnel", "get_person_by_badge")),
        java.util.Map.entry("create_leave", Set.of("get_leave_count", "get_leave_details")),
        java.util.Map.entry("create_cycle", Set.of("list_cycles", "get_cycle_details", "get_schedule_overview")),
        java.util.Map.entry("update_cycle", Set.of("list_cycles", "get_cycle_details")),
        java.util.Map.entry("delete_cycle", Set.of("list_cycles", "get_cycle_details")),
        java.util.Map.entry("reassign_duty", Set.of("list_cycles", "get_cycle_details", "get_schedule_overview")),
        java.util.Map.entry("auto_reassign_duty", Set.of("list_cycles", "get_cycle_details", "get_schedule_overview")),
        java.util.Map.entry("update_personnel", Set.of("search_personnel", "get_person_by_badge", "list_section_personnel")),
        java.util.Map.entry("create_adhoc_duty", Set.of("list_adhoc_duties", "get_adhoc_duty_details", "get_schedule_overview")),
        java.util.Map.entry("cancel_adhoc_duty", Set.of("list_adhoc_duties", "get_adhoc_duty_details", "get_schedule_overview"))
    );

    private final Cache<String, ToolExecutionResult> cache;
    private final ObjectMapper objectMapper;

    public ResponseCacheService(
            ObjectMapper objectMapper,
            @Value("${chat.cache.ttl-seconds:60}") long ttlSeconds) {
        this.objectMapper = objectMapper;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(500)
                .build();
    }

    public Optional<ToolExecutionResult> get(ActionPlan plan) {
        if (isWriteTool(plan.toolName())) {
            return Optional.empty();
        }
        String key = buildCacheKey(plan);
        ToolExecutionResult result = cache.getIfPresent(key);
        if (result != null) {
            log.debug("Cache hit for tool: {} key: {}", plan.toolName(), key);
        }
        return Optional.ofNullable(result);
    }

    public void put(ActionPlan plan, ToolExecutionResult result) {
        if (isWriteTool(plan.toolName())) {
            return;
        }
        String key = buildCacheKey(plan);
        cache.put(key, result);
        log.debug("Cached result for tool: {} key: {}", plan.toolName(), key);
    }

    public void invalidateForDomain(String toolName) {
        Set<String> relatedTools = INVALIDATION_MAP.get(toolName);
        if (relatedTools != null) {
            cache.asMap().keySet().removeIf(key -> {
                for (String related : relatedTools) {
                    if (key.startsWith(related + ":")) {
                        return true;
                    }
                }
                return false;
            });
            log.debug("Invalidated cache entries related to: {}", toolName);
        }
    }

    private boolean isWriteTool(String toolName) {
        return toolName != null && WRITE_TOOLS.contains(toolName);
    }

    private String buildCacheKey(ActionPlan plan) {
        String toolName = plan.toolName() != null ? plan.toolName() : "unknown";
        String paramsJson;
        try {
            TreeMap<String, Object> sorted = new TreeMap<>(plan.parameters() != null ? plan.parameters() : java.util.Map.of());
            paramsJson = objectMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            paramsJson = String.valueOf(plan.parameters());
        }
        return toolName + ":" + paramsJson;
    }
}
