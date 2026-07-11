package com.policescheduler.chat.gateway;

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
public class LanguageDetectorService {

    private static final Logger log = LoggerFactory.getLogger(LanguageDetectorService.class);

    public record LanguageDetectionResult(
        String detectedLanguage,
        String originalText,
        String canonicalEnglish,
        String responseLanguage
    ) {}

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    public LanguageDetectorService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public LanguageDetectionResult detect(String input) {
        if (input == null || input.isBlank()) {
            return new LanguageDetectionResult("en", input, input, "en");
        }

        boolean hasKannada = containsKannada(input);

        if (!hasKannada) {
            // Pure English — pass through, no LLM call
            return new LanguageDetectionResult("en", input, input, "en");
        }

        // Kannada or mixed input detected
        String canonicalEnglish = translateToEnglish(input);
        return new LanguageDetectionResult("kn", input, canonicalEnglish, "kn");
    }

    private boolean containsKannada(String input) {
        for (char c : input.toCharArray()) {
            // Kannada Unicode block: U+0C80 – U+0CFF
            if (c >= '\u0C80' && c <= '\u0CFF') {
                return true;
            }
        }
        return false;
    }

    private String translateToEnglish(String input) {
        try {
            String systemPrompt = "You are a translator. Translate the following Kannada or mixed Kannada-English text to English. " +
                "Preserve any English words as-is. Return ONLY the English translation, nothing else.";

            Map<String, Object> requestBody = Map.of(
                "model", "llama-3.1-8b-instant",
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", input)
                ),
                "max_tokens", 256,
                "temperature", 0.1
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                groqApiUrl, HttpMethod.POST, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    return choices.get(0).path("message").path("content").asText(input);
                }
            }
        } catch (Exception e) {
            log.error("Failed to translate Kannada input to English via Groq LLM", e);
        }
        // Fallback: return original input if translation fails
        return input;
    }
}
