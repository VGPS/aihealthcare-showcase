package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link ComparisonResult} domain record.
 *
 * <p>Verifies required field validation, minimum-two-results constraint,
 * defensive copies of both lists, and successful construction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
class ComparisonResultTest {

    private static final String         COMP_ID = "comp-001";
    private static final String         TOPIC   = "AI Healthcare";
    private static final NewsletterTone TONE    = NewsletterTone.PROFESSIONAL;
    private static final Instant        NOW     = Instant.parse("2026-04-17T00:00:00Z");

    private static final NewsletterSection SECTION = new NewsletterSection(
            "sec-001", SectionType.WHAT_SHIPPED, "AI Healthcare",
            "AI Advances", "Summary of advances.", List.of("art-001"));

    private static final EvaluationScore SCORE = new EvaluationScore(
            0.8, 0.9, 0.7, 0.8, 0.75, 0.79, "Notes");

    private static final EvaluationResult RESULT_1 = new EvaluationResult(
            "eval-001", "variant-a", "Variant A", List.of("art-001"),
            TOPIC, TONE, SECTION, SCORE, NOW);

    private static final EvaluationResult RESULT_2 = new EvaluationResult(
            "eval-002", "variant-b", "Variant B", List.of("art-001"),
            TOPIC, TONE, SECTION, SCORE, NOW);

    @Test
    void validComparison_constructsSuccessfully() {
        ComparisonResult comparison = new ComparisonResult(
                COMP_ID, List.of("art-001"), TOPIC, TONE,
                List.of(RESULT_1, RESULT_2), NOW);

        assertThat(comparison.comparisonId()).isEqualTo(COMP_ID);
        assertThat(comparison.articleIds()).containsExactly("art-001");
        assertThat(comparison.topic()).isEqualTo(TOPIC);
        assertThat(comparison.tone()).isEqualTo(TONE);
        assertThat(comparison.results()).hasSize(2);
        assertThat(comparison.comparedAt()).isEqualTo(NOW);
    }

    @Test
    void articleIds_areDefensivelyCopied() {
        List<String> mutable = new ArrayList<>();
        mutable.add("art-001");

        ComparisonResult comparison = new ComparisonResult(
                COMP_ID, mutable, TOPIC, TONE,
                List.of(RESULT_1, RESULT_2), NOW);

        mutable.add("art-002");
        assertThat(comparison.articleIds()).containsExactly("art-001");
    }

    @Test
    void results_areDefensivelyCopied() {
        List<EvaluationResult> mutable = new ArrayList<>();
        mutable.add(RESULT_1);
        mutable.add(RESULT_2);

        ComparisonResult comparison = new ComparisonResult(
                COMP_ID, List.of("art-001"), TOPIC, TONE, mutable, NOW);

        mutable.clear();
        assertThat(comparison.results()).hasSize(2);
    }

    @Test
    void nullComparisonId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ComparisonResult(
                null, List.of("art-001"), TOPIC, TONE,
                List.of(RESULT_1, RESULT_2), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comparisonId");
    }

    @Test
    void blankComparisonId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ComparisonResult(
                "  ", List.of("art-001"), TOPIC, TONE,
                List.of(RESULT_1, RESULT_2), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comparisonId");
    }

    @Test
    void emptyArticleIds_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ComparisonResult(
                COMP_ID, List.of(), TOPIC, TONE,
                List.of(RESULT_1, RESULT_2), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleIds");
    }

    @Test
    void blankTopic_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ComparisonResult(
                COMP_ID, List.of("art-001"), "", TONE,
                List.of(RESULT_1, RESULT_2), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void nullTone_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ComparisonResult(
                COMP_ID, List.of("art-001"), TOPIC, null,
                List.of(RESULT_1, RESULT_2), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tone");
    }

    @Test
    void singleResult_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ComparisonResult(
                COMP_ID, List.of("art-001"), TOPIC, TONE,
                List.of(RESULT_1), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("results");
    }

    @Test
    void nullResults_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ComparisonResult(
                COMP_ID, List.of("art-001"), TOPIC, TONE,
                null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("results");
    }

    @Test
    void nullComparedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ComparisonResult(
                COMP_ID, List.of("art-001"), TOPIC, TONE,
                List.of(RESULT_1, RESULT_2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comparedAt");
    }
}
