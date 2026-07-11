package com.policescheduler.chat.policy;

import com.policescheduler.chat.model.ActionPlan;
import com.policescheduler.chat.model.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private static final Set<String> WRITE_TOOLS = Set.of("add_person");

    public void authorize(ActionPlan plan, ProcessingContext context) {
        if (plan == null || context == null) {
            return;
        }
        // Multi-intent: check each sub-plan
        if ("multi_intent".equals(plan.intent()) && plan.subPlans() != null) {
            for (ActionPlan subPlan : plan.subPlans()) {
                authorize(subPlan, context);
            }
            return;
        }

        String toolName = plan.toolName();
        if (toolName != null && WRITE_TOOLS.contains(toolName)) {
            String userRole = context.getUserRole();
            if (userRole == null || !userRole.equalsIgnoreCase("ADMIN")) {
                log.warn("Authorization denied: user {} with role {} attempted write tool {}",
                        context.getUserId(), userRole, toolName);
                throw new PolicyViolationException("AUTHORIZATION",
                        "Write operation '" + toolName + "' requires ADMIN role");
            }
        }
        log.debug("Authorization passed for tool: {} with role: {}", plan.toolName(), context.getUserRole());
    }
}
