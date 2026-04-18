package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link EvaluationResult} domain record.
 *
 * <p>Verifies required field validation, defensive copy of {@code articleIds},
 * and that a valid result constructs successfully.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
class EvaluationResultTest {

    private static final String         EVAL_ID    = "eval-001";
    private static final String         VARIANT_ID = "summarize-v1";
    private static final String         VARIANT_NM = "Default V1";
    private static final String         TOPIC      = "AI Healthcare";
    private static final NewsletterTone TONE       = NewsletterTone.PROFESSIONAL;
    private static final Instant        NOW        = Instant.parse("2026-04-17T00:00:00Z");

    private static final NewsletterSection SECTION = new NewsletterSection(
            "sec-001", SectionType.WHAT_SHIPPED, "AI Healthcare",
            "AI Advances", "Summary of advances.", List.of("art-001"));

    private static final EvaluationScore SCORE = new EvaluationScore(
            0.8, 0.9, 0.7, 0.8, 0.75, 0.79, "Solid output");

    @Test
    void validResult_constructsSuccessfully() {
        EvaluationResult result = new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, List.of("art-001"),
                TOPIC, TONE, SECTION, SCORE, NOW);

        assertThat(result.evaluationId()).isEqualTo(EVAL_ID);
        assertThat(result.variantId()).isEqualTo(VARIANT_ID);
        assertThat(result.variantName()).isEqualTo(VARIANT_NM);
        assertThat(result.articleIds()).containsExactly("art-001");
        assertThat(result.topic()).isEqualTo(TOPIC);
        assertThat(result.tone()).isEqualTo(TONE);
        assertThat(result.section()).isEqualTo(SECTION);
        assertThat(result.score()).isEqualTo(SCORE);
        assertThat(result.evaluatedAt()).isEqualTo(NOW);
    }

    @Test
    void articleIds_areDefensivelyCopied() {
        List<String> mutable = new ArrayList<>();
        mutable.add("art-001");

        EvaluationResult result = new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, mutable,
                TOPIC, TONE, SECTION, SCORE, NOW);

        mutable.add("art-002");
        assertThat(result.articleIds()).containsExactly("art-001");
    }

    @Test
    void nullEvaluationId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                null, VARIANT_ID, VARIANT_NM, List.of("art-001"),
                TOPIC, TONE, SECTION, SCORE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluationId");
    }

    @Test
    void blankEvaluationId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                "  ", VARIANT_ID, VARIANT_NM, List.of("art-001"),
                TOPIC, TONE, SECTION, SCORE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluationId");
    }

    @Test
    void blankVariantId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                EVAL_ID, "", VARIANT_NM, List.of("art-001"),
                TOPIC, TONE, SECTION, SCORE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("variantId");
    }

    @Test
    void emptyArticleIds_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, List.of(),
                TOPIC, TONE, SECTION, SCORE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleIds");
    }

    @Test
    void nullArticleIds_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, null,
                TOPIC, TONE, SECTION, SCORE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleIds");
    }

    @Test
    void blankTopic_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, List.of("art-001"),
                "", TONE, SECTION, SCORE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void nullTone_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, List.of("art-001"),
                TOPIC, null, SECTION, SCORE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tone");
    }

    @Test
    void nullSection_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, List.of("art-001"),
                TOPIC, TONE, null, SCORE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("section");
    }

    @Test
    void nullScore_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, List.of("art-001"),
                TOPIC, TONE, SECTION, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }

    @Test
    void nullEvaluatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationResult(
                EVAL_ID, VARIANT_ID, VARIANT_NM, List.of("art-001"),
                TOPIC, TONE, SECTION, SCORE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluatedAt");
    }
}
