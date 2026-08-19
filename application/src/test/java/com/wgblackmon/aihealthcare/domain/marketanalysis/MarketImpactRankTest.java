package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MarketImpactRank} boundary validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class MarketImpactRankTest {

    @Test
    void rank1_valid() {
        MarketImpactRank rank = new MarketImpactRank(1);
        assertThat(rank.value()).isEqualTo(1);
    }

    @Test
    void rank5_valid() {
        MarketImpactRank rank = new MarketImpactRank(5);
        assertThat(rank.value()).isEqualTo(5);
    }

    @Test
    void rank0_throws() {
        assertThatThrownBy(() -> new MarketImpactRank(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rank must be between 1 and 5");
    }

    @Test
    void rank6_throws() {
        assertThatThrownBy(() -> new MarketImpactRank(6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rank must be between 1 and 5");
    }

    @Test
    void rankNegative_throws() {
        assertThatThrownBy(() -> new MarketImpactRank(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rank must be between 1 and 5");
    }
}
