package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.GapItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WikiGapResponseParser}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
class WikiGapResponseParserTest {

    private WikiGapResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new WikiGapResponseParser();
    }

    @Test
    void parseGaps_singleGap_parsedCorrectly() {
        String response = "GAPS:\n" +
                "TOPIC: AI Radiology Tools\n" +
                "ARTICLE_IDS: art-001, art-002, art-003\n" +
                "RECOMMENDATION: Create a DEEP_DIVE page covering AI radiology\n\n" +
                "SUMMARY:\nThe wiki has good coverage overall.";

        Set<String> validIds = Set.of("art-001", "art-002", "art-003", "art-004");
        List<GapItem> gaps = parser.parseGaps(response, validIds);

        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).topic()).isEqualTo("AI Radiology Tools");
        assertThat(gaps.get(0).articleIds()).containsExactly("art-001", "art-002", "art-003");
        assertThat(gaps.get(0).recommendation()).contains("DEEP_DIVE");
    }

    @Test
    void parseGaps_multipleGaps_allParsed() {
        String response = "GAPS:\n" +
                "TOPIC: Clinical NLP\n" +
                "ARTICLE_IDS: art-010, art-011\n" +
                "RECOMMENDATION: Create an OVERVIEW page\n" +
                "TOPIC: Digital Therapeutics\n" +
                "ARTICLE_IDS: art-020\n" +
                "RECOMMENDATION: Create a TRACKER page\n\n" +
                "SUMMARY:\nTwo major gaps identified.";

        Set<String> validIds = Set.of("art-010", "art-011", "art-020");
        List<GapItem> gaps = parser.parseGaps(response, validIds);

        assertThat(gaps).hasSize(2);
        assertThat(gaps.get(0).topic()).isEqualTo("Clinical NLP");
        assertThat(gaps.get(1).topic()).isEqualTo("Digital Therapeutics");
    }

    @Test
    void parseGaps_hallucatedIds_filtered() {
        String response = "GAPS:\n" +
                "TOPIC: Fake Topic\n" +
                "ARTICLE_IDS: art-001, hallucinated-id, art-002\n" +
                "RECOMMENDATION: Create a page\n\n" +
                "SUMMARY:\nSome coverage.";

        Set<String> validIds = Set.of("art-001", "art-002");
        List<GapItem> gaps = parser.parseGaps(response, validIds);

        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).articleIds()).containsExactly("art-001", "art-002");
    }

    @Test
    void parseGaps_emptyResponse_returnsEmptyList() {
        List<GapItem> gaps = parser.parseGaps("", Set.of());
        assertThat(gaps).isEmpty();
    }

    @Test
    void parseGaps_nullResponse_returnsEmptyList() {
        List<GapItem> gaps = parser.parseGaps(null, Set.of());
        assertThat(gaps).isEmpty();
    }

    @Test
    void parseGaps_noGapsSection_returnsEmptyList() {
        String response = "SUMMARY:\nEverything is covered.";
        List<GapItem> gaps = parser.parseGaps(response, Set.of());
        assertThat(gaps).isEmpty();
    }

    @Test
    void parseSummary_extractsSummarySection() {
        String response = "GAPS:\nTOPIC: Something\nARTICLE_IDS: art-001\n" +
                "RECOMMENDATION: Create page\n\n" +
                "SUMMARY:\nThe wiki has strong coverage of regulatory topics " +
                "but lacks clinical trial analysis.";

        String summary = parser.parseSummary(response);

        assertThat(summary).contains("strong coverage");
        assertThat(summary).contains("clinical trial");
    }

    @Test
    void parseSummary_noSummary_returnsDefault() {
        String result = parser.parseSummary("GAPS:\nTOPIC: Something");
        assertThat(result).isEqualTo("No summary available.");
    }

    @Test
    void parseSummary_null_returnsDefault() {
        String result = parser.parseSummary(null);
        assertThat(result).isEqualTo("No summary available.");
    }

    @Test
    void parseGaps_duplicateIds_deduplicated() {
        String response = "GAPS:\n" +
                "TOPIC: Duplicate Test\n" +
                "ARTICLE_IDS: art-001, art-001, art-002\n" +
                "RECOMMENDATION: Create page\n\n" +
                "SUMMARY:\nTest.";

        Set<String> validIds = Set.of("art-001", "art-002");
        List<GapItem> gaps = parser.parseGaps(response, validIds);

        assertThat(gaps.get(0).articleIds()).containsExactly("art-001", "art-002");
    }
}
