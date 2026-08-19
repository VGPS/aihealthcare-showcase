package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link GuidanceComparison} domain record.
 *
 * <p>Covers field validation in the compact constructor and confirms
 * that valid instances round-trip through their accessors correctly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class GuidanceComparisonTest {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWO = new BigDecimal("2.00");

    @Test
    void validComparison_createsSuccessfully() {
        GuidanceComparison g = new GuidanceComparison("NVDA", ONE, TWO, TWO, new BigDecimal("2.10"), "EPS");
        assertThat(g.tickerSymbol()).isEqualTo("NVDA");
        assertThat(g.metric()).isEqualTo("EPS");
        assertThat(g.priorGuidanceLow()).isEqualByComparingTo(ONE);
        assertThat(g.newGuidanceHigh()).isEqualByComparingTo(new BigDecimal("2.10"));
    }

    @Test
    void nullTicker_throws() {
        assertThatThrownBy(() -> new GuidanceComparison(null, ONE, TWO, ONE, TWO, "EPS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tickerSymbol");
    }

    @Test
    void blankTicker_throws() {
        assertThatThrownBy(() -> new GuidanceComparison("   ", ONE, TWO, ONE, TWO, "EPS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tickerSymbol");
    }

    @Test
    void nullPriorGuidanceLow_throws() {
        assertThatThrownBy(() -> new GuidanceComparison("NVDA", null, TWO, ONE, TWO, "EPS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priorGuidanceLow");
    }

    @Test
    void nullNewGuidanceHigh_throws() {
        assertThatThrownBy(() -> new GuidanceComparison("NVDA", ONE, TWO, ONE, null, "EPS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newGuidanceHigh");
    }

    @Test
    void nullMetric_throws() {
        assertThatThrownBy(() -> new GuidanceComparison("NVDA", ONE, TWO, ONE, TWO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metric");
    }

    @Test
    void blankMetric_throws() {
        assertThatThrownBy(() -> new GuidanceComparison("NVDA", ONE, TWO, ONE, TWO, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metric");
    }
}
