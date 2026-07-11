package com.policescheduler.chat.audit;

import com.policescheduler.chat.model.ChatAuditRecord;
import com.policescheduler.entity.ChatAuditLog;
import com.policescheduler.repository.ChatAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final ChatAuditLogRepository chatAuditLogRepository;

    public AuditLogService(ChatAuditLogRepository chatAuditLogRepository) {
        this.chatAuditLogRepository = chatAuditLogRepository;
    }

    @Async
    public void logInteraction(ChatAuditRecord record) {
        try {
            ChatAuditLog entity = ChatAuditLog.builder()
                    .userId(record.getUserId())
                    .sessionId(record.getSessionId())
                    .timestamp(record.getTimestamp() != null
                            ? LocalDateTime.ofInstant(record.getTimestamp(), ZoneId.systemDefault())
                            : LocalDateTime.now())
                    .originalInput(record.getOriginalInput())
                    .detectedLanguage(record.getDetectedLanguage())
                    .routingType(record.getRoutingType())
                    .actionPlanJson(record.getActionPlanJson())
                    .toolResultsJson(record.getToolResultsJson())
                    .finalResponse(record.getFinalResponse())
                    .totalTokens(record.getTotalTokens())
                    .promptTokens(record.getPromptTokens())
                    .completionTokens(record.getCompletionTokens())
                    .modelName(record.getModelName())
                    .latencyMs(record.getLatencyMs())
                    .cacheHit(record.isCacheHit())
                    .rejectionReason(record.getRejectionReason())
                    .build();

            chatAuditLogRepository.save(entity);
            log.debug("Audit log saved for user: {}", record.getUserId());
        } catch (Exception e) {
            log.error("Failed to persist audit log for user: {}", record.getUserId(), e);
        }
    }

    @Async
    public void logCircuitBreakerStateChange(String breakerName, String oldState, String newState) {
        try {
            log.info("Circuit breaker '{}' state changed: {} -> {}", breakerName, oldState, newState);
        } catch (Exception e) {
            log.error("Failed to log circuit breaker state change", e);
        }
    }

    @Async
    public void logRateLimitViolation(Long userId, String limitType) {
        try {
            log.warn("Rate limit violation: user={}, type={}", userId, limitType);
        } catch (Exception e) {
            log.error("Failed to log rate limit violation", e);
        }
    }
}
