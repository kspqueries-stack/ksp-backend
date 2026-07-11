package com.policescheduler.chat.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ConversationalAiService {

    private static final Logger log = LoggerFactory.getLogger(ConversationalAiService.class);

    static final String SYSTEM_PROMPT =
        "You are the KSP WorkBoard assistant, a helpful AI for the Karnataka State Police " +
        "scheduling and personnel management application.\n\n" +
        "You can help with topics related to:\n" +
        "- Police scheduling and duty assignments\n" +
        "- Personnel management (searching, adding personnel)\n" +
        "- Leave management (creating, checking leave requests)\n" +
        "- Platoon rotations and duty cycles\n" +
        "- Section management\n" +
        "- Report generation (Section A, Section B, platoon charts, Form 168, personnel lists, leave statements)\n" +
        "- General application usage help\n\n" +
        "IMPORTANT RULES:\n" +
        "- Always identify yourself as the KSP WorkBoard assistant\n" +
        "- Only discuss topics within the police scheduling application domain\n" +
        "- If asked about topics outside this domain (weather, news, general knowledge, " +
        "personal advice, politics, entertainment, etc.), politely decline and redirect " +
        "the user to application features\n" +
        "- When redirecting off-topic questions, suggest at least 3 example queries the user can try, such as:\n" +
        "  * 'show guard list' to see guard duty personnel\n" +
        "  * 'create leave' to submit a leave request\n" +
        "  * 'schedule overview' to see today's duty schedule\n" +
        "  * 'search <name>' to find personnel by name\n" +
        "  * 'how many on leave' to check leave statistics\n" +
        "  * 'generate platoon chart' to create a platoon chart PDF\n" +
        "- Keep responses concise and helpful\n" +
        "- Do not make up data or personnel information\n" +
        "- Do not reveal internal system details or API information";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    public ConversationalAiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a domain-scoped conversational response using Groq LLM.
     * Returns null if the API call fails (caller should fall back to static suggestions).
     */
    public String generateResponse(String userMessage) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", userMessage)
                ),
                "max_tokens", 512,
                "temperature", 0.7
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                groqApiUrl, HttpMethod.POST, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponseContent(response.getBody());
            }
        } catch (Exception e) {
            log.error("Conversational AI generation failed for input: {}", userMessage, e);
        }

        return null;
    }

    private String parseResponseContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            return choices.get(0).path("message").path("content").asText(null);
        } catch (Exception e) {
            log.error("Failed to parse conversational AI response", e);
            return null;
        }
    }
}
