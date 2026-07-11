package com.policescheduler.chat.router;

import com.policescheduler.chat.model.ActionPlan;
import com.policescheduler.chat.model.ProcessingContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DeterministicRouter {

    private final FuzzyMatcher fuzzyMatcher;
    private final MultiIntentDecomposer multiIntentDecomposer;

    // Duty type keyword → canonical duty_type value
    private static final Map<String, String> DUTY_TYPE_MAP = new LinkedHashMap<>();
    static {
        DUTY_TYPE_MAP.put("guard duty", "GUARD");
        DUTY_TYPE_MAP.put("guard list", "GUARD");
        DUTY_TYPE_MAP.put("guard", "GUARD");
        DUTY_TYPE_MAP.put("striking force", "STRIKING_FORCE");
        DUTY_TYPE_MAP.put("strike force", "STRIKING_FORCE");
        DUTY_TYPE_MAP.put("check post", "CHECK_POST");
        DUTY_TYPE_MAP.put("dog squad", "DOG_SQUAD");
        DUTY_TYPE_MAP.put("gunman", "GUNMAN");
        DUTY_TYPE_MAP.put("asc team", "ASC_TEAM");
        DUTY_TYPE_MAP.put("armoury", "ARMOURY");
        DUTY_TYPE_MAP.put("driver", "DRIVER");
        DUTY_TYPE_MAP.put("drivers", "DRIVER");
        DUTY_TYPE_MAP.put("drivers list", "DRIVER");
        DUTY_TYPE_MAP.put("mt section", "DRIVER");
        DUTY_TYPE_MAP.put("motor transport", "DRIVER");
        DUTY_TYPE_MAP.put("escort", "ESCORT");
        DUTY_TYPE_MAP.put("ood", "OOD");
        DUTY_TYPE_MAP.put("canteen", "CANTEEN");
        DUTY_TYPE_MAP.put("band", "BAND");
        DUTY_TYPE_MAP.put("qrt", "QRT");
        DUTY_TYPE_MAP.put("cpt", "CPT");
        DUTY_TYPE_MAP.put("photographer", "PHOTOGRAPHER");
        DUTY_TYPE_MAP.put("bugler", "BUGLER");
        DUTY_TYPE_MAP.put("chamber sentry", "CHAMBER_SENTRY");
        DUTY_TYPE_MAP.put("garden duty", "GARDEN_DUTY");
    }

    private static final List<String> DESIGNATIONS = List.of(
        "DCP", "ACP", "RPI", "RSI", "ARSI", "AHC", "APC"
    );

    // Report-related patterns — checked before other patterns
    private static final Pattern REPORT_SECTION_AB = Pattern.compile(
        "(?:generate|download|get|create)\\s+section\\s+a\\s*(?:&|and)\\s*b\\s+(?:report|pdf)",
        Pattern.CASE_INSENSITIVE);

    // Section-specific platoon chart: "generate section D platoon chart"
    private static final Pattern REPORT_PLATOON_CHART_SECTION = Pattern.compile(
        "(?:generate|download|get|create)\\s+section\\s+([C-Gc-g])\\s+(?:platoon\\s+chart|chart|report|pdf)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern REPORT_PLATOON_CHART = Pattern.compile(
        "(?:generate|download|get|create)\\s+(?:platoon\\s+chart|section\\s+c\\s+(?:report|chart|pdf))",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern REPORT_FORM168 = Pattern.compile(
        "(?:generate|download|get|create)\\s+(?:form\\s*(?:no\\.?\\s*)?168|daily\\s+statement|form168)\\s*(?:report|pdf)?",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern REPORT_SECTION_A = Pattern.compile(
        "(?:generate|download|get|create)\\s+section\\s+a\\s+(?:report|pdf)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern REPORT_SECTION_B = Pattern.compile(
        "(?:generate|download|get|create)\\s+section\\s+b\\s+(?:report|pdf)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern REPORT_MT_SECTION = Pattern.compile(
        "(?:generate|download|get|create)\\s+(?:mt\\s+section|drivers?\\s+list|car\\s+mt)\\s*(?:report|pdf)?",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern REPORT_PERSONNEL = Pattern.compile(
        "(?:generate|download|get|create)\\s+personnel\\s+(?:list|report)\\s*(?:pdf)?",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern REPORT_LEAVE = Pattern.compile(
        "(?:generate|download|get|create)\\s+leave\\s+(?:statement|report)\\s*(?:pdf)?",
        Pattern.CASE_INSENSITIVE);

    // Regex patterns for each tool
    // Leave creation — must be checked BEFORE LEAVE_PATTERN (which matches read operations)
    private static final Pattern CREATE_LEAVE_PATTERN = Pattern.compile(
        "(?:create|submit|apply(?:\\s+for)?|request|new)\\s+leave" +
        "|leave\\s+request\\s+for" +
        "|apply\\s+leave",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern ADD_PERSON_PATTERN = Pattern.compile(
        "(?:add|create|new)\\s+(?:person|personnel|staff|member)\\s*(.*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SEARCH_PERSONNEL_PATTERN = Pattern.compile(
        "(?:search|find|who\\s+is|look\\s*up)\\s+(.*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern LIST_ALL_PERSONNEL_PATTERN = Pattern.compile(
        "(?:list\\s+all|show\\s+all|all\\s+personnel|everyone|persons\\s+list|staff\\s+list)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "(?:section\\s+([A-Ga-g])(?:\\s+list)?|([A-Ga-g])\\s+section(?:\\s+list)?|HQ\\s+staff|office\\s+staff)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern BADGE_PATTERN = Pattern.compile(
        "(?:badge|badge\\s+id|badge\\s+number)\\s+([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SCHEDULE_PATTERN = Pattern.compile(
        "(?:schedule\\s+overview|duty\\s+schedule|today'?s?\\s+schedule|\\bschedule\\b)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern LEAVE_PATTERN = Pattern.compile(
        "(?:leave\\s+count|how\\s+many\\s+on\\s+leave|leave\\s+status|leave\\s+requests|\\bleave\\b)",
        Pattern.CASE_INSENSITIVE);

    // Cycle management patterns — checked BEFORE ROTATION_PATTERN to avoid false matches
    private static final Pattern CREATE_CYCLE_PATTERN = Pattern.compile(
        "(?:create|new|setup|publish)\\s+(?:platoon\\s+cycle|shift\\s+cycle|rotation\\s+(?:schedule|cycle))" +
        "|(?:setup|create)\\s+rotation\\s+schedule" +
        "|new\\s+platoon\\s+cycle" +
        "|publish\\s+cycle",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern LIST_CYCLES_PATTERN = Pattern.compile(
        "(?:list|show|view|get)\\s+(?:all\\s+)?cycles" +
        "|(?:list|show|view)\\s+(?:platoon\\s+)?(?:rotation\\s+)?cycles",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern DELETE_CYCLE_PATTERN = Pattern.compile(
        "(?:delete|remove|deactivate)\\s+(?:platoon\\s+)?cycle",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern ROTATION_PATTERN = Pattern.compile(
        "(?:platoon\\s+rotation|platoon\\s+cycle|platoon\\s+schedule|platoon\\s+chart|platoon\\s+duty|\\brotation\\b)",
        Pattern.CASE_INSENSITIVE);

    // All known tool keywords for fuzzy matching
    private static final List<String> TOOL_KEYWORDS = List.of(
        "search", "find", "guard", "striking force", "check post", "section",
        "badge", "schedule", "leave", "rotation", "platoon", "add person",
        "personnel", "designation", "dog squad", "gunman", "escort", "driver",
        "create leave", "apply leave", "submit leave", "request leave",
        "create cycle", "platoon cycle", "shift cycle", "rotation schedule",
        "list cycles", "delete cycle", "publish cycle"
    );

    public DeterministicRouter(
            FuzzyMatcher fuzzyMatcher,
            MultiIntentDecomposer multiIntentDecomposer) {
        this.fuzzyMatcher = fuzzyMatcher;
        this.multiIntentDecomposer = multiIntentDecomposer;
    }

    public Optional<ActionPlan> route(String normalizedInput, ProcessingContext context) {
        if (normalizedInput == null || normalizedInput.isBlank()) {
            return Optional.empty();
        }

        // Expand abbreviations first
        String expanded = fuzzyMatcher.expandAbbreviations(normalizedInput.trim());

        // Decompose multi-intent queries
        List<String> segments = multiIntentDecomposer.decompose(expanded);

        if (segments.size() > 1) {
            return routeMultiIntent(segments, normalizedInput, context);
        }

        // Single intent routing
        return routeSingle(expanded, normalizedInput);
    }

    private Optional<ActionPlan> routeMultiIntent(List<String> segments, String rawInput, ProcessingContext context) {
        List<ActionPlan> subPlans = segments.stream()
            .map(seg -> routeSingle(seg, rawInput))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        if (subPlans.isEmpty()) {
            return Optional.empty();
        }

        if (subPlans.size() == 1) {
            return Optional.of(subPlans.get(0));
        }

        // Return a composite ActionPlan with subPlans
        return Optional.of(new ActionPlan(
            "multi_intent",
            null,
            Map.of(),
            1.0,
            "deterministic",
            false,
            subPlans,
            rawInput,
            rawInput
        ));
    }

    private Optional<ActionPlan> routeSingle(String input, String rawInput) {
        String lower = input.toLowerCase().trim();

        // Report patterns — check before other patterns to avoid false matches
        // Check section A&B combined before individual section A/B
        if (REPORT_SECTION_AB.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_section_ab_pdf",
                Map.of(), rawInput, input));
        }
        // Section-specific platoon chart: "generate section D platoon chart"
        Matcher platoonSectionMatcher = REPORT_PLATOON_CHART_SECTION.matcher(lower);
        if (platoonSectionMatcher.find()) {
            String sectionVal = platoonSectionMatcher.group(1).toUpperCase();
            return Optional.of(ActionPlan.of("generate_report", "generate_platoon_chart_pdf",
                Map.of("section", sectionVal), rawInput, input));
        }
        if (REPORT_PLATOON_CHART.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_platoon_chart_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_FORM168.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_form168_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_SECTION_A.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_section_a_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_SECTION_B.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_section_b_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_MT_SECTION.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_mt_section_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_PERSONNEL.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_personnel_list_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_LEAVE.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_leave_statement_pdf",
                Map.of(), rawInput, input));
        }

        // 1. add_person
        Matcher m = ADD_PERSON_PATTERN.matcher(lower);
        if (m.find()) {
            String name = m.group(1) != null ? m.group(1).trim() : "";
            return Optional.of(ActionPlan.of("add_person", "add_person",
                name.isEmpty() ? Map.of() : Map.of("name", name), rawInput, input));
        }

        // 2. search_by_duty_type — check before generic search
        for (Map.Entry<String, String> entry : DUTY_TYPE_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return Optional.of(ActionPlan.of("search_by_duty_type", "search_by_duty_type",
                    Map.of("duty_type", entry.getValue()), rawInput, input));
            }
        }

        // 3. search_by_designation
        for (String desig : DESIGNATIONS) {
            Pattern desigPattern = Pattern.compile(
                "\\b" + desig + "\\b\\s*(?:list|officers|all)?", Pattern.CASE_INSENSITIVE);
            if (desigPattern.matcher(lower).find()) {
                return Optional.of(ActionPlan.of("search_by_designation", "search_by_designation",
                    Map.of("designation", desig), rawInput, input));
            }
        }

        // 4. list_section_personnel
        m = SECTION_PATTERN.matcher(lower);
        if (m.find()) {
            String section;
            if (lower.contains("hq staff") || lower.contains("office staff")) {
                section = "HQ";
            } else {
                section = m.group(1) != null ? m.group(1).toUpperCase() : m.group(2).toUpperCase();
            }
            return Optional.of(ActionPlan.of("list_section_personnel", "list_section_personnel",
                Map.of("section", section), rawInput, input));
        }

        // 5. get_person_by_badge
        m = BADGE_PATTERN.matcher(lower);
        if (m.find()) {
            return Optional.of(ActionPlan.of("get_person_by_badge", "get_person_by_badge",
                Map.of("badge_id", m.group(1).trim()), rawInput, input));
        }

        // 6. search_personnel (list all variant)
        if (LIST_ALL_PERSONNEL_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("search_personnel", "search_personnel",
                Map.of("query", ""), rawInput, input));
        }

        // 7. Cycle management patterns — check BEFORE rotation to avoid "platoon cycle" matching generic rotation
        if (CREATE_CYCLE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("create_cycle", "create_cycle",
                Map.of(), rawInput, input));
        }

        if (LIST_CYCLES_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("list_cycles", "list_cycles",
                Map.of(), rawInput, input));
        }

        if (DELETE_CYCLE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("delete_cycle", "delete_cycle",
                Map.of(), rawInput, input));
        }

        // 8. get_platoon_rotation — check before schedule/leave to avoid false matches
        if (ROTATION_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("get_platoon_rotation", "get_platoon_rotation",
                Map.of(), rawInput, input));
        }

        // 8. get_schedule_overview
        if (SCHEDULE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("get_schedule_overview", "get_schedule_overview",
                Map.of(), rawInput, input));
        }

        // 9. create_leave — must be checked BEFORE get_leave_count to avoid false routing
        if (CREATE_LEAVE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("create_leave", "create_leave",
                Map.of(), rawInput, input));
        }

        // 10. get_leave_count
        if (LEAVE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("get_leave_count", "get_leave_count",
                Map.of(), rawInput, input));
        }

        // 11. search_personnel (generic search/find)
        m = SEARCH_PERSONNEL_PATTERN.matcher(lower);
        if (m.find()) {
            String query = m.group(1) != null ? m.group(1).trim() : "";
            return Optional.of(ActionPlan.of("search_personnel", "search_personnel",
                Map.of("query", query), rawInput, input));
        }

        // 11. Fuzzy matching fallback — try matching input words against known keywords
        return tryFuzzyMatch(lower, rawInput);
    }

    private Optional<ActionPlan> tryFuzzyMatch(String input, String rawInput) {
        String[] words = input.split("\\s+");
        for (String word : words) {
            if (word.length() < 3) continue;

            List<FuzzyMatcher.MatchCandidate> matches = fuzzyMatcher.findMatches(word, TOOL_KEYWORDS);
            if (!matches.isEmpty()) {
                String bestMatch = matches.get(0).candidate();
                // Re-route using the corrected keyword
                String corrected = input.replace(word, bestMatch);
                Optional<ActionPlan> result = routeSingleWithoutFuzzy(corrected, rawInput);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ActionPlan> routeSingleWithoutFuzzy(String input, String rawInput) {
        String lower = input.toLowerCase().trim();

        // Report patterns — check before other patterns
        if (REPORT_SECTION_AB.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_section_ab_pdf",
                Map.of(), rawInput, input));
        }
        // Section-specific platoon chart: "generate section D platoon chart"
        Matcher platoonSectionMatcher = REPORT_PLATOON_CHART_SECTION.matcher(lower);
        if (platoonSectionMatcher.find()) {
            String sectionVal = platoonSectionMatcher.group(1).toUpperCase();
            return Optional.of(ActionPlan.of("generate_report", "generate_platoon_chart_pdf",
                Map.of("section", sectionVal), rawInput, input));
        }
        if (REPORT_PLATOON_CHART.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_platoon_chart_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_FORM168.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_form168_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_SECTION_A.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_section_a_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_SECTION_B.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_section_b_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_MT_SECTION.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_mt_section_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_PERSONNEL.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_personnel_list_pdf",
                Map.of(), rawInput, input));
        }
        if (REPORT_LEAVE.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("generate_report", "generate_leave_statement_pdf",
                Map.of(), rawInput, input));
        }

        Matcher m = ADD_PERSON_PATTERN.matcher(lower);
        if (m.find()) {
            String name = m.group(1) != null ? m.group(1).trim() : "";
            return Optional.of(ActionPlan.of("add_person", "add_person",
                name.isEmpty() ? Map.of() : Map.of("name", name), rawInput, input));
        }

        for (Map.Entry<String, String> entry : DUTY_TYPE_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return Optional.of(ActionPlan.of("search_by_duty_type", "search_by_duty_type",
                    Map.of("duty_type", entry.getValue()), rawInput, input));
            }
        }

        for (String desig : DESIGNATIONS) {
            Pattern desigPattern = Pattern.compile(
                "\\b" + desig + "\\b\\s*(?:list|officers|all)?", Pattern.CASE_INSENSITIVE);
            if (desigPattern.matcher(lower).find()) {
                return Optional.of(ActionPlan.of("search_by_designation", "search_by_designation",
                    Map.of("designation", desig), rawInput, input));
            }
        }

        m = SECTION_PATTERN.matcher(lower);
        if (m.find()) {
            String section;
            if (lower.contains("hq staff") || lower.contains("office staff")) {
                section = "HQ";
            } else {
                section = m.group(1) != null ? m.group(1).toUpperCase() : m.group(2).toUpperCase();
            }
            return Optional.of(ActionPlan.of("list_section_personnel", "list_section_personnel",
                Map.of("section", section), rawInput, input));
        }

        m = BADGE_PATTERN.matcher(lower);
        if (m.find()) {
            return Optional.of(ActionPlan.of("get_person_by_badge", "get_person_by_badge",
                Map.of("badge_id", m.group(1).trim()), rawInput, input));
        }

        if (LIST_ALL_PERSONNEL_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("search_personnel", "search_personnel",
                Map.of("query", ""), rawInput, input));
        }

        // Cycle management patterns — check BEFORE rotation to avoid "platoon cycle" matching generic rotation
        if (CREATE_CYCLE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("create_cycle", "create_cycle",
                Map.of(), rawInput, input));
        }

        if (LIST_CYCLES_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("list_cycles", "list_cycles",
                Map.of(), rawInput, input));
        }

        if (DELETE_CYCLE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("delete_cycle", "delete_cycle",
                Map.of(), rawInput, input));
        }

        if (ROTATION_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("get_platoon_rotation", "get_platoon_rotation",
                Map.of(), rawInput, input));
        }

        if (SCHEDULE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("get_schedule_overview", "get_schedule_overview",
                Map.of(), rawInput, input));
        }

        // create_leave — must be checked BEFORE get_leave_count to avoid false routing
        if (CREATE_LEAVE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("create_leave", "create_leave",
                Map.of(), rawInput, input));
        }

        if (LEAVE_PATTERN.matcher(lower).find()) {
            return Optional.of(ActionPlan.of("get_leave_count", "get_leave_count",
                Map.of(), rawInput, input));
        }

        m = SEARCH_PERSONNEL_PATTERN.matcher(lower);
        if (m.find()) {
            String query = m.group(1) != null ? m.group(1).trim() : "";
            return Optional.of(ActionPlan.of("search_personnel", "search_personnel",
                Map.of("query", query), rawInput, input));
        }

        return Optional.empty();
    }
}
