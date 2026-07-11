package com.policescheduler.chat.router;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class MultiIntentDecomposer {

    private static final Pattern SPLIT_PATTERN = Pattern.compile(
        "\\s+and\\s+|\\s+also\\s+|,\\s+|\\s+then\\s+|\\s+plus\\s+",
        Pattern.CASE_INSENSITIVE
    );

    public List<String> decompose(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        List<String> segments = Arrays.stream(SPLIT_PATTERN.split(input))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        if (segments.isEmpty()) {
            return List.of(input.trim());
        }

        return segments;
    }
}
