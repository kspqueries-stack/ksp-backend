package com.policescheduler.chat.model;

import java.util.List;
import java.util.Map;

public record ActionPlan(
    String intent,
    String toolName,
    Map<String, Object> parameters,
    double confidenceScore,
    String routingType,  // "deterministic" | "llm" | "rag"
    boolean ragRequired,
    List<ActionPlan> subPlans,
    String rawInput,
    String normalizedInput
) {
    public static ActionPlan unknown(String rawInput, String normalizedInput) {
        return new ActionPlan("unknown", null, Map.of(), 0.0, "deterministic", false, null, rawInput, normalizedInput);
    }

    public static ActionPlan of(String intent, String toolName, Map<String, Object> parameters, String rawInput, String normalizedInput) {
        return new ActionPlan(intent, toolName, parameters, 1.0, "deterministic", false, null, rawInput, normalizedInput);
    }
}
