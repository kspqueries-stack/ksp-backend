package com.policescheduler.chat.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class CircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManager.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerManager() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(java.time.Duration.ofSeconds(60))
                .minimumNumberOfCalls(3)
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(defaultConfig);
    }

    public <T> T execute(String breakerName, Supplier<T> action, Supplier<T> fallback) {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(breakerName);
        try {
            return breaker.executeSupplier(action);
        } catch (Exception e) {
            log.warn("Circuit breaker '{}' triggered fallback: {}", breakerName, e.getMessage());
            return fallback.get();
        }
    }

    public String getState(String breakerName) {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(breakerName);
        return breaker.getState().name();
    }
}
