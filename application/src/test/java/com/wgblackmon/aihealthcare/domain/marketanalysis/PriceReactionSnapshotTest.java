package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PriceReactionSnapshot} validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
class PriceReactionSnapshotTest {

    private static final Instant NOW = Instant.now();
    private static final BigDecimal BASELINE = new BigDecimal("10.00");
    private static final BigDecimal OBSERVED = new BigDecimal("10.80");
    private static final BigDecimal PCT_CHANGE = new BigDecimal("8.00");

    @Test
    void validSnapshot_createsSuccessfully() {
        PriceReactionSnapshot snapshot = new PriceReactionSnapshot(
                "entry-1", "DOCS", ReactionHorizon.ONE_DAY, BASELINE, OBSERVED, PCT_CHANGE, NOW);

        assertThat(snapshot.entryId()).isEqualTo("entry-1");
        assertThat(snapshot.tickerSymbol()).isEqualTo("DOCS");
        assertThat(snapshot.horizon()).isEqualTo(ReactionHorizon.ONE_DAY);
        assertThat(snapshot.baselinePrice()).isEqualByComparingTo(BASELINE);
        assertThat(snapshot.observedPrice()).isEqualByComparingTo(OBSERVED);
        assertThat(snapshot.pctChange()).isEqualByComparingTo(PCT_CHANGE);
        assertThat(snapshot.measuredAt()).isEqualTo(NOW);
    }

    @Test
    void blankEntryId_throws() {
        assertThatThrownBy(() -> new PriceReactionSnapshot(
                " ", "DOCS", ReactionHorizon.ONE_DAY, BASELINE, OBSERVED, PCT_CHANGE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryId");
    }

    @Test
    void blankTickerSymbol_throws() {
        assertThatThrownBy(() -> new PriceReactionSnapshot(
                "entry-1", "", ReactionHorizon.ONE_DAY, BASELINE, OBSERVED, PCT_CHANGE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tickerSymbol");
    }

    @Test
    void nullHorizon_throws() {
        assertThatThrownBy(() -> new PriceReactionSnapshot(
                "entry-1", "DOCS", null, BASELINE, OBSERVED, PCT_CHANGE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("horizon");
    }

    @Test
    void nullBaselinePrice_throws() {
        assertThatThrownBy(() -> new PriceReactionSnapshot(
                "entry-1", "DOCS", ReactionHorizon.ONE_DAY, null, OBSERVED, PCT_CHANGE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baselinePrice");
    }

    @Test
    void nullObservedPrice_throws() {
        assertThatThrownBy(() -> new PriceReactionSnapshot(
                "entry-1", "DOCS", ReactionHorizon.ONE_DAY, BASELINE, null, PCT_CHANGE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observedPrice");
    }

    @Test
    void nullPctChange_throws() {
        assertThatThrownBy(() -> new PriceReactionSnapshot(
                "entry-1", "DOCS", ReactionHorizon.ONE_DAY, BASELINE, OBSERVED, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pctChange");
    }

    @Test
    void nullMeasuredAt_throws() {
        assertThatThrownBy(() -> new PriceReactionSnapshot(
                "entry-1", "DOCS", ReactionHorizon.ONE_DAY, BASELINE, OBSERVED, PCT_CHANGE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("measuredAt");
    }
}
