package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.time.Duration;

/**
 * Fixed time offsets after a {@link MarketNewsItem#publishedAt()} at which
 * {@link PriceReactionService} checks whether the market has reacted.
 *
 * <p>{@code ONE_HOUR} and {@code FOUR_HOUR} are measured against a live quote
 * ({@code MarketDataPort#getQuote}) since Alpaca's free-tier bars are daily-only.
 * {@code ONE_DAY} and {@code THREE_DAY} are measured against the closing price
 * of the appropriate trading day instead, which is more stable than whatever
 * the live price happens to be when the poller runs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
public enum ReactionHorizon {

    ONE_HOUR(Duration.ofHours(1)),
    FOUR_HOUR(Duration.ofHours(4)),
    ONE_DAY(Duration.ofDays(1)),
    THREE_DAY(Duration.ofDays(3));

    private final Duration offset;

    ReactionHorizon(Duration offset) {
        this.offset = offset;
    }

    /**
     * Returns the time offset to add to {@code publishedAt} to compute this
     * horizon's due time.
     */
    public Duration offset() {
        return offset;
    }

    /**
     * Returns {@code true} when this horizon should be measured against a live
     * quote rather than a daily closing bar (i.e. {@code ONE_HOUR} or {@code FOUR_HOUR}).
     */
    public boolean isIntraday() {
        return this == ONE_HOUR || this == FOUR_HOUR;
    }
}
