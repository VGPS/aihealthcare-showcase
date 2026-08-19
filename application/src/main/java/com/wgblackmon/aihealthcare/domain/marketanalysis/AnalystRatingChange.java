package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable record capturing a single analyst rating change for a publicly traded company.
 *
 * <p>Rating strings (e.g. "BUY", "OUTPERFORM", "NEUTRAL", "UNDERWEIGHT") are stored as
 * plain strings because each analyst firm uses its own proprietary rating scale.
 * Price targets are nullable — not all rating changes include updated price targets.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record AnalystRatingChange(
        String firm,
        String tickerSymbol,
        String previousRating,
        String newRating,
        BigDecimal previousPriceTarget,
        BigDecimal newPriceTarget,
        Instant changedAt
) {
    public AnalystRatingChange {
        if (firm == null || firm.isBlank()) {
            throw new IllegalArgumentException("firm must not be blank");
        }
        if (tickerSymbol == null || tickerSymbol.isBlank()) {
            throw new IllegalArgumentException("tickerSymbol must not be blank");
        }
        if (previousRating == null || previousRating.isBlank()) {
            throw new IllegalArgumentException("previousRating must not be blank");
        }
        if (newRating == null || newRating.isBlank()) {
            throw new IllegalArgumentException("newRating must not be blank");
        }
        if (changedAt == null) {
            throw new IllegalArgumentException("changedAt must not be null");
        }
    }
}
