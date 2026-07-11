package com.policescheduler.controller;

import com.policescheduler.config.LlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String aiProvider;
    private final LlmClient llmClient;

    public HealthController(
            @Value("${app.ai-provider:groq}") String aiProvider,
            LlmClient llmClient) {
        this.aiProvider = aiProvider;
        this.llmClient = llmClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "aiProvider", aiProvider
        ));
    }

    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> checkAiStatus() {
        try {
            // Simple ping — send a tiny request to verify the AI provider is reachable
            List<Map<String, Object>> messages = List.of(
                    Map.of("role", "user", "content", "hi")
            );
            String response = llmClient.chatCompletion(null, messages);
            boolean ok = response != null && !response.isBlank();
            return ResponseEntity.ok(Map.of("connected", ok, "provider", aiProvider));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("connected", false, "reason", e.getMessage(), "provider", aiProvider));
        }
    }
}
