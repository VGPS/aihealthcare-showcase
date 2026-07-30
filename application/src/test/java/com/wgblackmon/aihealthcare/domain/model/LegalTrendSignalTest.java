package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link LegalTrendSignal} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
class LegalTrendSignalTest {

    @Test
    void validSignal_createsSuccessfully() {
        LegalTrendSignal signal = new LegalTrendSignal(
                "FDA enforcement", "REGULATION", 10, 3,
                3.33, TrendDirection.RISING, List.of("a1", "a2"), "Summary text");

        assertThat(signal.keyword()).isEqualTo("FDA enforcement");
        assertThat(signal.category()).isEqualTo("REGULATION");
        assertThat(signal.current30d()).isEqualTo(10);
        assertThat(signal.previous90d()).isEqualTo(3);
        assertThat(signal.momentum()).isEqualTo(3.33);
        assertThat(signal.direction()).isEqualTo(TrendDirection.RISING);
        assertThat(signal.topArticleIds()).containsExactly("a1", "a2");
        assertThat(signal.summary()).isEqualTo("Summary text");
    }

    @Test
    void nullKeyword_throws() {
        assertThatThrownBy(() -> new LegalTrendSignal(
                null, "LITIGATION", 5, 2, 2.5, TrendDirection.RISING, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");
    }

    @Test
    void blankKeyword_throws() {
        assertThatThrownBy(() -> new LegalTrendSignal(
                "  ", "LITIGATION", 5, 2, 2.5, TrendDirection.RISING, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");
    }

    @Test
    void nullCategory_throws() {
        assertThatThrownBy(() -> new LegalTrendSignal(
                "HIPAA", null, 5, 2, 2.5, TrendDirection.RISING, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");
    }

    @Test
    void negativeCurrent30d_throws() {
        assertThatThrownBy(() -> new LegalTrendSignal(
                "HIPAA", "LITIGATION", -1, 2, 2.5, TrendDirection.RISING, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current30d");
    }

    @Test
    void nullDirection_throws() {
        assertThatThrownBy(() -> new LegalTrendSignal(
                "HIPAA", "LITIGATION", 5, 2, 2.5, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void nullArticleIds_defaultsToEmptyList() {
        LegalTrendSignal signal = new LegalTrendSignal(
                "HIPAA", "LITIGATION", 5, 2, 2.5, TrendDirection.RISING, null);

        assertThat(signal.topArticleIds()).isEmpty();
    }

    @Test
    void convenienceConstructor_setsNullSummary() {
        LegalTrendSignal signal = new LegalTrendSignal(
                "HIPAA", "LITIGATION", 5, 2, 2.5, TrendDirection.RISING, List.of("a1"));

        assertThat(signal.summary()).isNull();
    }

    @Test
    void topArticleIds_areDefensivelyCopied() {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        ids.add("a1");

        LegalTrendSignal signal = new LegalTrendSignal(
                "HIPAA", "LITIGATION", 5, 2, 2.5, TrendDirection.RISING, ids);

        assertThatThrownBy(() -> signal.topArticleIds().add("a2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
