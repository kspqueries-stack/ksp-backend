package com.policescheduler.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class PiiFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PiiFilter.class);

    private static final Set<String> PII_PHONE_FIELDS = Set.of("phoneNumber", "phone_number");
    private static final Set<String> PII_EMAIL_FIELDS = Set.of("email");
    private static final Set<String> PII_BADGE_FIELDS = Set.of("badgeId", "badge_id");

    private final ObjectMapper objectMapper;

    public PiiFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, responseWrapper);

        if (isAdminUser()) {
            responseWrapper.copyBodyToResponse();
            return;
        }

        String contentType = responseWrapper.getContentType();
        if (contentType == null || !contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            responseWrapper.copyBodyToResponse();
            return;
        }

        byte[] responseBody = responseWrapper.getContentAsByteArray();
        if (responseBody.length == 0) {
            responseWrapper.copyBodyToResponse();
            return;
        }

        try {
            String body = new String(responseBody, StandardCharsets.UTF_8);
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode masked = maskPiiFields(jsonNode);
            byte[] maskedBytes = objectMapper.writeValueAsBytes(masked);

            response.setContentLength(maskedBytes.length);
            response.getOutputStream().write(maskedBytes);
            response.getOutputStream().flush();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse response body for PII masking", e);
            responseWrapper.copyBodyToResponse();
        }
    }

    private boolean isAdminUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN"));
    }

    private JsonNode maskPiiFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode child = objectNode.get(fieldName);
                if (child.isTextual()) {
                    String value = child.asText();
                    if (PII_PHONE_FIELDS.contains(fieldName)) {
                        objectNode.put(fieldName, maskPhone(value));
                    } else if (PII_EMAIL_FIELDS.contains(fieldName)) {
                        objectNode.put(fieldName, maskEmail(value));
                    } else if (PII_BADGE_FIELDS.contains(fieldName)) {
                        objectNode.put(fieldName, maskBadgeId(value));
                    }
                } else if (child.isObject() || child.isArray()) {
                    objectNode.set(fieldName, maskPiiFields(child));
                }
            });
            return objectNode;
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                arrayNode.set(i, maskPiiFields(arrayNode.get(i)));
            }
            return arrayNode;
        }
        return node;
    }

    /**
     * Mask phone number: keep last 4 digits → "XXX-XXX-1234"
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        String lastFour = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return "XXX-XXX-" + lastFour;
    }

    /**
     * Mask email: first char + *** + @domain → "x***@domain.com"
     */
    static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***" + email;
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * Mask badge ID: keep last 3 chars → "***456"
     */
    static String maskBadgeId(String badgeId) {
        if (badgeId == null || badgeId.isEmpty()) {
            return badgeId;
        }
        String lastThree = badgeId.length() >= 3 ? badgeId.substring(badgeId.length() - 3) : badgeId;
        return "***" + lastThree;
    }
}
