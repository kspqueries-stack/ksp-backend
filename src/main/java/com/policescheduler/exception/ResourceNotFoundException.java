package com.policescheduler.exception;

/**
 * Exception thrown when a requested resource (duty type, section, etc.) is not found.
 * Distinct from JPA's EntityNotFoundException — this is thrown from service-level code
 * when an entity lookup by ID or code returns empty.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s not found with identifier: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getResourceId() {
        return resourceId;
    }
}
