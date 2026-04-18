package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link EvaluationScore} domain record.
 *
 * <p>Verifies that all five score dimensions plus the overall score are
 * validated to the [0.0, 1.0] range and that valid scores construct
 * successfully.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
class EvaluationScoreTest {

    @Test
    void validScore_constructsSuccessfully() {
        EvaluationScore score = new EvaluationScore(0.85, 0.90, 0.75, 0.80, 0.70, 0.80, "Good overall");

        assertThat(score.relevance()).isEqualTo(0.85);
        assertThat(score.conciseness()).isEqualTo(0.90);
        assertThat(score.attributionQuality()).isEqualTo(0.75);
        assertThat(score.toneMatch()).isEqualTo(0.80);
        assertThat(score.completeness()).isEqualTo(0.70);
        assertThat(score.overall()).isEqualTo(0.80);
        assertThat(score.scoringNotes()).isEqualTo("Good overall");
    }

    @Test
    void boundaryValues_zeroAndOne_constructSuccessfully() {
        EvaluationScore score = new EvaluationScore(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null);

        assertThat(score.relevance()).isEqualTo(0.0);

        EvaluationScore perfect = new EvaluationScore(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, "Perfect");

        assertThat(perfect.relevance()).isEqualTo(1.0);
    }

    @Test
    void nullScoringNotes_isAllowed() {
        EvaluationScore score = new EvaluationScore(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, null);

        assertThat(score.scoringNotes()).isNull();
    }

    @Test
    void relevanceAboveOne_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationScore(1.1, 0.5, 0.5, 0.5, 0.5, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relevance");
    }

    @Test
    void relevanceBelowZero_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationScore(-0.1, 0.5, 0.5, 0.5, 0.5, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relevance");
    }

    @Test
    void concisenessAboveOne_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationScore(0.5, 1.1, 0.5, 0.5, 0.5, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conciseness");
    }

    @Test
    void attributionQualityAboveOne_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationScore(0.5, 0.5, 1.1, 0.5, 0.5, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributionQuality");
    }

    @Test
    void toneMatchAboveOne_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationScore(0.5, 0.5, 0.5, 1.1, 0.5, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toneMatch");
    }

    @Test
    void completenessAboveOne_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationScore(0.5, 0.5, 0.5, 0.5, 1.1, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completeness");
    }

    @Test
    void overallAboveOne_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationScore(0.5, 0.5, 0.5, 0.5, 0.5, 1.1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overall");
    }

    @Test
    void overallBelowZero_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EvaluationScore(0.5, 0.5, 0.5, 0.5, 0.5, -0.1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overall");
    }
}
