package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single price-reaction measurement for one {@link MarketDigestEntry} at one
 * {@link ReactionHorizon} after the underlying news item was published.
 *
 * <p>{@code baselinePrice} is the last close strictly before the news item's
 * {@code publishedAt}; {@code observedPrice} is either a live quote (intraday
 * horizons) or a closing bar (daily horizons) captured at or after the horizon's
 * due time. {@code pctChange} is the percentage move between the two, e.g.
 * {@code 8.25} for a +8.25% move.
 *
 * <p>Produced by {@link PriceReactionService} and persisted via {@link port.PriceReactionPort}.
 *
 * @param entryId        the {@code MarketDigestEntry} this snapshot measures (required, non-blank)
 * @param tickerSymbol   exchange ticker symbol (required, non-blank)
 * @param horizon        which fixed offset this snapshot measures (required, non-null)
 * @param baselinePrice  last close before the news broke (required, non-null)
 * @param observedPrice  price observed at or after the horizon's due time (required, non-null)
 * @param pctChange      percentage change from baseline to observed (required, non-null)
 * @param measuredAt     when this snapshot was actually captured (required, non-null)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
public record PriceReactionSnapshot(
        String entryId,
        String tickerSymbol,
        ReactionHorizon horizon,
        BigDecimal baselinePrice,
        BigDecimal observedPrice,
        BigDecimal pctChange,
        Instant measuredAt
) {

    public PriceReactionSnapshot {
        if (entryId == null || entryId.isBlank()) {
            throw new IllegalArgumentException("entryId must not be blank");
        }
        if (tickerSymbol == null || tickerSymbol.isBlank()) {
            throw new IllegalArgumentException("tickerSymbol must not be blank");
        }
        if (horizon == null) {
            throw new IllegalArgumentException("horizon must not be null");
        }
        if (baselinePrice == null) {
            throw new IllegalArgumentException("baselinePrice must not be null");
        }
        if (observedPrice == null) {
            throw new IllegalArgumentException("observedPrice must not be null");
        }
        if (pctChange == null) {
            throw new IllegalArgumentException("pctChange must not be null");
        }
        if (measuredAt == null) {
            throw new IllegalArgumentException("measuredAt must not be null");
        }
    }
}
