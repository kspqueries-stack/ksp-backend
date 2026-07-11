package com.policescheduler.chat.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final ConcurrentHashMap<Long, Deque<Instant>> generalRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Deque<Instant>> llmRequests = new ConcurrentHashMap<>();

    private final int generalLimit;
    private final int llmLimit;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public RateLimiterService(
            @Value("${chat.rate-limit.general:30}") int generalLimit,
            @Value("${chat.rate-limit.llm:5}") int llmLimit) {
        this.generalLimit = generalLimit;
        this.llmLimit = llmLimit;
    }

    public void checkRateLimit(Long userId, boolean isLlmRouted) {
        Instant now = Instant.now();

        // Check general rate limit
        Deque<Instant> generalTimestamps = generalRequests.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        if (!isWithinLimit(generalTimestamps, generalLimit, now)) {
            log.warn("General rate limit exceeded for user: {}", userId);
            throw new RateLimitExceededException("general", WINDOW);
        }
        generalTimestamps.addLast(now);

        // Check LLM rate limit if applicable
        if (isLlmRouted) {
            Deque<Instant> llmTimestamps = llmRequests.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
            if (!isWithinLimit(llmTimestamps, llmLimit, now)) {
                log.warn("LLM rate limit exceeded for user: {}", userId);
                throw new RateLimitExceededException("llm", WINDOW);
            }
            llmTimestamps.addLast(now);
        }
    }

    private boolean isWithinLimit(Deque<Instant> timestamps, int maxRequests, Instant now) {
        Instant windowStart = now.minus(WINDOW);
        // Remove expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }
        return timestamps.size() < maxRequests;
    }
}
