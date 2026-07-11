package com.policescheduler.chat.router;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FuzzyMatcher {

    public record MatchCandidate(String candidate, int distance, double confidence) {}

    private static final int MAX_DISTANCE_THRESHOLD = 2;

    private static final Map<String, String> ABBREVIATION_MAP = Map.ofEntries(
        Map.entry("sf", "striking force"),
        Map.entry("cp", "check post"),
        Map.entry("sec", "section"),
        Map.entry("grd", "guard"),
        Map.entry("pers", "personnel"),
        Map.entry("sched", "schedule"),
        Map.entry("rot", "rotation"),
        Map.entry("plt", "platoon"),
        Map.entry("desig", "designation"),
        Map.entry("lv", "leave"),
        Map.entry("plantoon", "platoon"),
        Map.entry("pltn", "platoon")
    );

    public List<MatchCandidate> findMatches(String input, List<String> candidates) {
        if (input == null || input.isBlank() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String normalizedInput = input.toLowerCase().trim();

        return candidates.stream()
            .map(candidate -> {
                int dist = levenshteinDistance(normalizedInput, candidate.toLowerCase().trim());
                double confidence = dist == 0 ? 1.0 : Math.max(0.0, 1.0 - (double) dist / Math.max(normalizedInput.length(), candidate.length()));
                return new MatchCandidate(candidate, dist, confidence);
            })
            .filter(mc -> mc.distance() <= MAX_DISTANCE_THRESHOLD)
            .sorted(Comparator.comparingInt(MatchCandidate::distance))
            .collect(Collectors.toList());
    }

    public String expandAbbreviations(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String result = input;
        for (Map.Entry<String, String> entry : ABBREVIATION_MAP.entrySet()) {
            // Case-insensitive whole-word replacement
            result = result.replaceAll("(?i)\\b" + entry.getKey() + "\\b", entry.getValue());
        }
        return result;
    }

    static int levenshteinDistance(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null ? 0 : Integer.MAX_VALUE;
        }

        int lenA = a.length();
        int lenB = b.length();
        int[][] dp = new int[lenA + 1][lenB + 1];

        for (int i = 0; i <= lenA; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= lenB; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[lenA][lenB];
    }
}
