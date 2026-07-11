package com.policescheduler.chat.rag;

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
public class ReasonerLlmService {

    private static final Logger log = LoggerFactory.getLogger(ReasonerLlmService.class);

    private static final String REASONER_PROMPT =
        "You are a reasoning assistant for a police management system. " +
        "Given the user's query and relevant document context, determine the best action. " +
        "Respond ONLY with JSON: {\"intent\":\"...\",\"tool_name\":\"...\",\"parameters\":{...},\"rag_required\":false}";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    public ReasonerLlmService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public ActionPlan refine(ActionPlan plan, List<String> ragContext, ProcessingContext context) {
        if (!plan.ragRequired() || ragContext == null || ragContext.isEmpty()) {
            return plan;
        }

        try {
            String contextText = String.join("\n---\n", ragContext);
            String userMessage = "Query: " + plan.normalizedInput() + "\n\nRelevant context:\n" + contextText;

            Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", List.of(
                    Map.of("role", "system", "content", REASONER_PROMPT),
                    Map.of("role", "user", "content", userMessage)
                ),
                "max_tokens", 512,
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
                return parseRefinedPlan(response.getBody(), plan);
            }
        } catch (Exception e) {
            log.error("Reasoner LLM refinement failed, returning original plan", e);
        }
        return plan;
    }

    private ActionPlan parseRefinedPlan(String responseBody, ActionPlan originalPlan) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return originalPlan;
            }

            String content = choices.get(0).path("message").path("content").asText("");
            String jsonStr = extractJson(content);
            JsonNode parsed = objectMapper.readTree(jsonStr);

            String intent = parsed.path("intent").asText(originalPlan.intent());
            String toolName = parsed.path("tool_name").asText(originalPlan.toolName());

            Map<String, Object> parameters = new HashMap<>();
            JsonNode paramsNode = parsed.path("parameters");
            if (paramsNode.isObject()) {
                var fields = paramsNode.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    parameters.put(field.getKey(), field.getValue().asText());
                }
            }

            return new ActionPlan(
                intent, toolName, parameters, 0.9, "rag", false,
                null, originalPlan.rawInput(), originalPlan.normalizedInput()
            );
        } catch (Exception e) {
            log.error("Failed to parse reasoner LLM response", e);
            return originalPlan;
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
