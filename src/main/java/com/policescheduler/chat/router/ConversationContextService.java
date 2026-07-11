package com.policescheduler.chat.router;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Service
public class ConversationContextService {

    private static final Logger log = LoggerFactory.getLogger(ConversationContextService.class);

    private static final int MAX_MESSAGES_PER_USER = 5;

    private record ConversationEntry(String sessionId, String message, String response) {}

    private final Cache<Long, LinkedList<ConversationEntry>> contextCache;

    public ConversationContextService() {
        this.contextCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(15))
                .maximumSize(1000)
                .build();
    }

    public void addMessage(Long userId, String sessionId, String message, String response) {
        LinkedList<ConversationEntry> entries = contextCache.get(userId, k -> new LinkedList<>());
        synchronized (entries) {
            entries.addLast(new ConversationEntry(sessionId, message, response));
            while (entries.size() > MAX_MESSAGES_PER_USER) {
                entries.removeFirst();
            }
        }
        log.debug("Added conversation entry for user {}, total entries: {}", userId, entries.size());
    }

    public List<String> getRecentMessages(Long userId, int count) {
        LinkedList<ConversationEntry> entries = contextCache.getIfPresent(userId);
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (entries) {
            int start = Math.max(0, entries.size() - count);
            List<String> messages = new ArrayList<>();
            for (int i = start; i < entries.size(); i++) {
                ConversationEntry e = entries.get(i);
                messages.add("User: " + e.message() + "\nAssistant: " + e.response());
            }
            return messages;
        }
    }

    public String resolveContext(Long userId, String currentMessage) {
        LinkedList<ConversationEntry> entries = contextCache.getIfPresent(userId);
        if (entries == null || entries.isEmpty()) {
            return currentMessage;
        }

        String lower = currentMessage.toLowerCase();
        // Check for pronouns/references that need context resolution
        boolean hasReference = lower.contains("their") || lower.contains("them")
                || lower.contains("his") || lower.contains("her")
                || lower.contains("that person") || lower.contains("same")
                || lower.contains("those") || lower.contains("it")
                || lower.matches(".*\\bnow\\s+(section|duty|designation)\\b.*");

        if (!hasReference) {
            return currentMessage;
        }

        synchronized (entries) {
            if (entries.isEmpty()) return currentMessage;
            // Get the last entry for context
            ConversationEntry last = entries.getLast();
            return currentMessage + " [context: previous query was '" + last.message() + "']";
        }
    }
}
