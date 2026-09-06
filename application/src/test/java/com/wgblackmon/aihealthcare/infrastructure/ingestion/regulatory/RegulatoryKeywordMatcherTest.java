package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RegulatoryKeywordMatcher}.
 *
 * <p>Verifies word-boundary regex matching: short keywords like "AI"
 * must NOT match substrings ("brain"), but MUST match standalone
 * occurrences and hyphenated compounds ("AI-powered").
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
class RegulatoryKeywordMatcherTest {

    @Test
    void aiMatchesStandalone() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "FDA clears AI diagnostic tool", List.of("AI"));
        assertThat(result).containsExactly("AI");
    }

    @Test
    void aiMatchesHyphenatedCompound() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "AI-powered imaging system cleared", List.of("AI"));
        assertThat(result).containsExactly("AI");
    }

    @Test
    void aiDoesNotMatchSubstring() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "brain imaging strain relief", List.of("AI"));
        assertThat(result).isEmpty();
    }

    @Test
    void caseInsensitive() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "samd device cleared by fda", List.of("SaMD"));
        assertThat(result).containsExactly("SaMD");
    }

    @Test
    void multiWordKeywordMatches() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "New clinical decision support system approved",
                List.of("clinical decision support"));
        assertThat(result).containsExactly("clinical decision support");
    }

    @Test
    void multipleKeywordsMatch() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "AI-powered machine learning diagnostic software",
                List.of("AI", "machine learning", "diagnostic software", "neural network"));
        assertThat(result).containsExactly("AI", "machine learning", "diagnostic software");
    }

    @Test
    void nullTextReturnsEmpty() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(null, List.of("AI"));
        assertThat(result).isEmpty();
    }

    @Test
    void blankTextReturnsEmpty() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords("   ", List.of("AI"));
        assertThat(result).isEmpty();
    }

    @Test
    void computerAidedMatchesHyphenated() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "Computer-aided detection mammography system",
                List.of("computer-aided"));
        assertThat(result).containsExactly("computer-aided");
    }

    @Test
    void algorithmDoesNotMatchAlgorithmic() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "algorithmic trading platform", List.of("algorithm"));
        assertThat(result).isEmpty();
    }

    @Test
    void algorithmMatchesStandalone() {
        List<String> result = RegulatoryKeywordMatcher.matchKeywords(
                "FDA-cleared algorithm for cardiac imaging", List.of("algorithm"));
        assertThat(result).containsExactly("algorithm");
    }
}
