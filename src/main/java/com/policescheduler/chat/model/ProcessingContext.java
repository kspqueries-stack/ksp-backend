package com.policescheduler.chat.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessingContext {
    private Long userId;
    private String userRole;
    private String sessionId;
    private String detectedLanguage;  // "en" or "kn"
    private String responseLanguage;  // "en" or "kn"
    private String originalInput;
    private String normalizedInput;   // English-normalized for routing
    private List<String> conversationHistory;
}
