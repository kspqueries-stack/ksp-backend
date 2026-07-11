package com.policescheduler.chat.policy;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final Duration cooldown;
    private final String limitType;

    public RateLimitExceededException(String limitType, Duration cooldown) {
        super("Rate limit exceeded (" + limitType + "). Please wait " + cooldown.getSeconds() + " seconds.");
        this.limitType = limitType;
        this.cooldown = cooldown;
    }

    public Duration getCooldown() {
        return cooldown;
    }

    public String getLimitType() {
        return limitType;
    }
}
