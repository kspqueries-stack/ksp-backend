package com.policescheduler.exception;

/**
 * Exception thrown when an operation cannot be completed due to a conflict
 * with the current state of a resource (e.g., active assignments blocking deactivation).
 */
public class ConflictException extends RuntimeException {

    private final int blockingCount;

    public ConflictException(String message, int blockingCount) {
        super(message);
        this.blockingCount = blockingCount;
    }

    public ConflictException(String message) {
        this(message, 0);
    }

    public int getBlockingCount() {
        return blockingCount;
    }
}
