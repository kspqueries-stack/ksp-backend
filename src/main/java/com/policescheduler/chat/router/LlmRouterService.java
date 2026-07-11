package com.policescheduler.chat.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policescheduler.chat.model.ActionPlan;
import com.policescheduler.chat.model.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmRouterService {

    private static final Logger log = LoggerFactory.getLogger(LlmRouterService.class);

    private static final String ROUTING_PROMPT =
        "You are a routing classifier for a police management system. " +
        "Classify the user's intent into one of these categories and extract parameters.\n\n" +
        "Categories: search_personnel, search_by_duty_type, search_by_designation, " +
        "list_section_personnel, get_person_by_badge, get_schedule_overview, get_leave_count, " +
        "get_platoon_rotation, add_person, create_leave, rag_query, unknown\n\n" +
        "Respond ONLY with JSON: {\"intent\":\"...\",\"tool_name\":\"...\",\"parameters\":{...},\"rag_required\":false}";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    public LlmRouterService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public ActionPlan route(String normalizedInput, ProcessingContext context) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", "llama-3.1-8b-instant",
                "messages", List.of(
                    Map.of("role", "system", "content", ROUTING_PROMPT),
                    Map.of("role", "user", "content", normalizedInput)
                ),
                "max_tokens", 256,
                "temperature", 0.0
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                groqApiUrl, HttpMethod.POST, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody(), normalizedInput);
            }
        } catch (Exception e) {
            log.error("LLM routing failed for input: {}", normalizedInput, e);
        }

        return unknownWithSuggestions(normalizedInput);
    }

    private ActionPlan parseResponse(String responseBody, String normalizedInput) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return unknownWithSuggestions(normalizedInput);
            }

            String content = choices.get(0).path("message").path("content").asText("");

            // Extract JSON from the response (handle markdown code blocks)
            String jsonStr = extractJson(content);
            JsonNode parsed = objectMapper.readTree(jsonStr);

            String intent = parsed.path("intent").asText("unknown");
            String toolName = parsed.path("tool_name").asText(null);
            boolean ragRequired = parsed.path("rag_required").asBoolean(false);

            Map<String, Object> parameters = new HashMap<>();
            JsonNode paramsNode = parsed.path("parameters");
            if (paramsNode.isObject()) {
                var fields = paramsNode.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    parameters.put(field.getKey(), field.getValue().asText());
                }
            }

            if ("rag_query".equals(intent)) {
                ragRequired = true;
            }

            return new ActionPlan(
                intent,
                toolName,
                parameters,
                0.8,
                "llm",
                ragRequired,
                null,
                normalizedInput,
                normalizedInput
            );
        } catch (Exception e) {
            log.error("Failed to parse LLM routing response", e);
            return unknownWithSuggestions(normalizedInput);
        }
    }

    private String extractJson(String content) {
        // Strip markdown code block if present
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        // Find first { and last }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private ActionPlan unknownWithSuggestions(String normalizedInput) {
        Map<String, Object> params = Map.of(
            "suggestions", List.of(
                "Try: 'show guard list'",
                "Try: 'how many on leave'",
                "Try: 'search <name>'",
                "Try: 'schedule overview'"
            )
        );
        return new ActionPlan(
            "unknown",
            null,
            params,
            0.0,
            "llm",
            false,
            null,
            normalizedInput,
            normalizedInput
        );
    }
}
