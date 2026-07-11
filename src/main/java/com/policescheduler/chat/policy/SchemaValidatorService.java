package com.policescheduler.chat.policy;

import com.policescheduler.chat.model.ActionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SchemaValidatorService {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidatorService.class);

    public void validate(ActionPlan plan) {
        if (plan == null) {
            throw new PolicyViolationException("SCHEMA_VALIDATION", "ActionPlan must not be null");
        }
        if (plan.intent() == null || plan.intent().isBlank()) {
            throw new PolicyViolationException("SCHEMA_VALIDATION", "ActionPlan intent must not be null or blank");
        }
        // For non-unknown intents, toolName is required
        if (!"unknown".equals(plan.intent()) && !"multi_intent".equals(plan.intent())) {
            if (plan.toolName() == null || plan.toolName().isBlank()) {
                throw new PolicyViolationException("SCHEMA_VALIDATION",
                        "ActionPlan toolName is required for intent: " + plan.intent());
            }
        }
        // Validate sub-plans recursively for multi-intent
        if ("multi_intent".equals(plan.intent()) && plan.subPlans() != null) {
            for (ActionPlan subPlan : plan.subPlans()) {
                validate(subPlan);
            }
        }
        log.debug("ActionPlan schema validation passed for intent: {}", plan.intent());
    }
}
