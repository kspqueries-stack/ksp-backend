package com.policescheduler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM client for Groq (OpenAI-compatible API).
 * Used in local development and as a fallback in production.
 */
public class GroqLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmClient.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String apiUrl;
    private final String defaultModel;

    public GroqLlmClient(RestTemplate restTemplate, String apiKey, String apiUrl, String defaultModel) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.defaultModel = defaultModel;
    }

    @Override
    public String chatCompletion(String model, List<Map<String, Object>> messages,
                                  List<Map<String, Object>> tools, Object toolChoice) {
        String useModel = (model != null && !model.isBlank()) ? model : defaultModel;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", useModel);
        requestBody.put("messages", messages);

        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
            if (toolChoice != null) {
                requestBody.put("tool_choice", toolChoice);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl + "/chat/completions", HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        }

        throw new RuntimeException("Groq API call failed with status: " + response.getStatusCode());
    }

    @Override
    public String getProviderName() {
        return "groq";
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }
}
