package com.policescheduler.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "original_input", nullable = false, columnDefinition = "TEXT")
    private String originalInput;

    @Column(name = "detected_language", length = 5)
    private String detectedLanguage;

    @Column(name = "routing_type", length = 20)
    private String routingType;

    @Column(name = "action_plan_json", columnDefinition = "jsonb")
    private String actionPlanJson;

    @Column(name = "tool_results_json", columnDefinition = "jsonb")
    private String toolResultsJson;

    @Column(name = "final_response", columnDefinition = "TEXT")
    private String finalResponse;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "cache_hit")
    private Boolean cacheHit;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (totalTokens == null) {
            totalTokens = 0;
        }
        if (promptTokens == null) {
            promptTokens = 0;
        }
        if (completionTokens == null) {
            completionTokens = 0;
        }
        if (cacheHit == null) {
            cacheHit = false;
        }
    }
}
