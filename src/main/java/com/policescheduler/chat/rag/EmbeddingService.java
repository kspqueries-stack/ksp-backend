package com.policescheduler.chat.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${embedding.api.url:https://api.openai.com/v1/embeddings}")
    private String embeddingApiUrl;

    @Value("${embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    public EmbeddingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public float[] generateEmbedding(String text) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", embeddingModel,
                "input", text
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                embeddingApiUrl, HttpMethod.POST, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode embeddingNode = root.path("data").get(0).path("embedding");
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                return embedding;
            }
        } catch (Exception e) {
            log.error("Failed to generate embedding for text: {}", text.substring(0, Math.min(50, text.length())), e);
        }
        return new float[0];
    }

    public List<float[]> generateBatchEmbeddings(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        try {
            Map<String, Object> requestBody = Map.of(
                "model", embeddingModel,
                "input", texts
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                embeddingApiUrl, HttpMethod.POST, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode dataNode = root.path("data");
                for (JsonNode item : dataNode) {
                    JsonNode embeddingNode = item.path("embedding");
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    embeddings.add(embedding);
                }
            }
        } catch (Exception e) {
            log.error("Failed to generate batch embeddings", e);
        }
        return embeddings;
    }
}
