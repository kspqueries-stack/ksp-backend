package com.policescheduler.chat.policy;

public class PolicyViolationException extends RuntimeException {

    private final String violationType;

    public PolicyViolationException(String message) {
        super(message);
        this.violationType = "POLICY_VIOLATION";
    }

    public PolicyViolationException(String violationType, String message) {
        super(message);
        this.violationType = violationType;
    }

    public String getViolationType() {
        return violationType;
    }
}
