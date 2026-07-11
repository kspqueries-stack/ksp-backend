package com.policescheduler.chat.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ChatAuditRecord {
    private Long userId;
    private String sessionId;
    private Instant timestamp;
    private String originalInput;
    private String detectedLanguage;
    private String routingType;
    private String actionPlanJson;
    private String toolResultsJson;
    private String finalResponse;
    private int totalTokens;
    private int promptTokens;
    private int completionTokens;
    private String modelName;
    private long latencyMs;
    private boolean cacheHit;
    private String rejectionReason;
}
