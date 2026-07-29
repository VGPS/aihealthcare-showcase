package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ExtractedTrend} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
class ExtractedTrendTest {

    @Test
    void validRecordCreation() {
        ExtractedTrend trend = new ExtractedTrend(
                "FDA AI Clearances",
                "Rising number of FDA clearances for AI diagnostic tools",
                List.of(0, 3, 7));

        assertThat(trend.label()).isEqualTo("FDA AI Clearances");
        assertThat(trend.description()).isEqualTo("Rising number of FDA clearances for AI diagnostic tools");
        assertThat(trend.articleIndices()).containsExactly(0, 3, 7);
    }

    @Test
    void nullLabelThrows() {
        assertThatThrownBy(() -> new ExtractedTrend(null, "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    @Test
    void blankLabelThrows() {
        assertThatThrownBy(() -> new ExtractedTrend("  ", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    @Test
    void nullDescriptionThrows() {
        assertThatThrownBy(() -> new ExtractedTrend("Label", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void blankDescriptionThrows() {
        assertThatThrownBy(() -> new ExtractedTrend("Label", "  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void nullIndicesDefaultsToEmpty() {
        ExtractedTrend trend = new ExtractedTrend("Label", "Desc", null);
        assertThat(trend.articleIndices()).isEmpty();
    }

    @Test
    void indicesAreDefensiveCopied() {
        java.util.ArrayList<Integer> mutable = new java.util.ArrayList<>();
        mutable.add(1);
        mutable.add(2);
        ExtractedTrend trend = new ExtractedTrend("Label", "Desc", mutable);

        assertThat(trend.articleIndices()).containsExactly(1, 2);
        assertThatThrownBy(() -> trend.articleIndices().add(3))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
