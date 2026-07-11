package com.policescheduler.chat.gateway;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class InputSanitizerService {

    public record SanitizeResult(String sanitizedText, boolean promptInjectionDetected) {}

    private static final int MAX_INPUT_LENGTH = 2000;

    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
        Pattern.compile("\\bDROP\\s+TABLE\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bDELETE\\s+FROM\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bINSERT\\s+INTO\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bUPDATE\\s+\\w+\\s+SET\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bUNION\\s+SELECT\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("--"),
        Pattern.compile(";\\s*--")
    );

    private static final List<Pattern> XSS_PATTERNS = List.of(
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<img[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bonclick\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bonerror\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
        Pattern.compile("\\bignore\\s+previous\\s+instructions\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bignore\\s+all\\s+instructions\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bforget\\s+your\\s+instructions\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bnew\\s+instructions\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\byou\\s+are\\s+now\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*system\\s*:", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("^\\s*assistant\\s*:", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("^\\s*user\\s*:", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
    );

    public SanitizeResult sanitize(String input) {
        if (input == null || input.isBlank()) {
            return new SanitizeResult("", false);
        }

        boolean promptInjectionDetected = detectPromptInjection(input);

        String sanitized = input;

        // Strip SQL injection patterns
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }

        // Strip XSS / HTML patterns
        for (Pattern pattern : XSS_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }

        // Strip prompt injection patterns
        for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }

        // Enforce max length
        if (sanitized.length() > MAX_INPUT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_INPUT_LENGTH);
            promptInjectionDetected = true;
        }

        sanitized = sanitized.trim();

        return new SanitizeResult(sanitized, promptInjectionDetected);
    }

    private boolean detectPromptInjection(String input) {
        for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
}
