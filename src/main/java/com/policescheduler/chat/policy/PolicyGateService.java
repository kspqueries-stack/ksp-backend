package com.policescheduler.chat.policy;

import com.policescheduler.chat.model.ActionPlan;
import com.policescheduler.chat.model.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PolicyGateService {

    private static final Logger log = LoggerFactory.getLogger(PolicyGateService.class);

    private final SchemaValidatorService schemaValidator;
    private final AuthorizationService authorizationService;
    private final RateLimiterService rateLimiterService;

    public PolicyGateService(SchemaValidatorService schemaValidator,
                             AuthorizationService authorizationService,
                             RateLimiterService rateLimiterService) {
        this.schemaValidator = schemaValidator;
        this.authorizationService = authorizationService;
        this.rateLimiterService = rateLimiterService;
    }

    public ActionPlan validate(ActionPlan plan, ProcessingContext context) {
        log.debug("Policy gate validating plan with intent: {}", plan != null ? plan.intent() : "null");

        // 1. Schema validation
        schemaValidator.validate(plan);

        // 2. Authorization check
        authorizationService.authorize(plan, context);

        // 3. Rate limit check
        boolean isLlmRouted = "llm".equals(plan.routingType()) || "rag".equals(plan.routingType());
        rateLimiterService.checkRateLimit(context.getUserId(), isLlmRouted);

        log.debug("Policy gate passed for intent: {}", plan.intent());
        return plan;
    }
}
