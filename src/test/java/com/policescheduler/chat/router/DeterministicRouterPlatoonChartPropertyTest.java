package com.policescheduler.chat.router;

import com.policescheduler.chat.model.ActionPlan;
import net.jqwik.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for DeterministicRouter section-specific platoon chart routing.
 * Feature: platoon-chart-multi-section, Property 9: Chat router extracts section from section-specific requests
 */
class DeterministicRouterPlatoonChartPropertyTest {

    // --- Property 9: Chat router extracts section from section-specific requests ---
    // Feature: platoon-chart-multi-section, Property 9: Chat router extracts section from section-specific requests

    @Property(tries = 100)
    void sectionSpecificRequest_extractsCorrectSection(
            @ForAll("validSections") String section,
            @ForAll("actionVerbs") String verb) {

        FuzzyMatcher fuzzyMatcher = mock(FuzzyMatcher.class);
        MultiIntentDecomposer decomposer = mock(MultiIntentDecomposer.class);

        when(fuzzyMatcher.expandAbbreviations(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        DeterministicRouter router = new DeterministicRouter(fuzzyMatcher, decomposer);

        String input = verb + " section " + section + " platoon chart";

        when(decomposer.decompose(anyString()))
                .thenReturn(java.util.List.of(input));

        Optional<ActionPlan> result = router.route(input, null);

        assertTrue(result.isPresent(), "Should route section-specific platoon chart request");
        assertEquals("generate_platoon_chart_pdf", result.get().toolName());
        assertEquals(section.toUpperCase(), result.get().parameters().get("section"),
                "Should extract section " + section.toUpperCase());
    }

    @Property(tries = 100)
    void genericPlatoonChartRequest_noSectionParameter(
            @ForAll("actionVerbs") String verb) {

        FuzzyMatcher fuzzyMatcher = mock(FuzzyMatcher.class);
        MultiIntentDecomposer decomposer = mock(MultiIntentDecomposer.class);

        when(fuzzyMatcher.expandAbbreviations(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        DeterministicRouter router = new DeterministicRouter(fuzzyMatcher, decomposer);

        String input = verb + " platoon chart";

        when(decomposer.decompose(anyString()))
                .thenReturn(java.util.List.of(input));

        Optional<ActionPlan> result = router.route(input, null);

        assertTrue(result.isPresent(), "Should route generic platoon chart request");
        assertEquals("generate_platoon_chart_pdf", result.get().toolName());
        assertFalse(result.get().parameters().containsKey("section"),
                "Generic request should NOT have section parameter");
    }

    @Provide
    Arbitrary<String> validSections() {
        return Arbitraries.of("C", "D", "E", "F", "G", "c", "d", "e", "f", "g");
    }

    @Provide
    Arbitrary<String> actionVerbs() {
        return Arbitraries.of("generate", "download", "get", "create");
    }
}
