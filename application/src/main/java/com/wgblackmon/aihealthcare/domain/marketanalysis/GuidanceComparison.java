package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.math.BigDecimal;

/**
 * Immutable record capturing a single guidance comparison for a publicly traded company.
 *
 * <p>Stores prior guidance (the company's previous forecast range) alongside new guidance
 * (updated forecast or actual result), enabling beat/miss calculations for market-impact
 * analysis. The {@code metric} field identifies what is being compared (e.g. "EPS",
 * "REVENUE", "OPERATING_MARGIN").
 *
 * <p>Both guidance ranges may be null for a single-point guidance figure, in which case
 * {@code priorGuidanceLow == priorGuidanceHigh} and similarly for new guidance.
 * The constructor enforces non-null required fields but allows the range bounds to differ.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record GuidanceComparison(
        String tickerSymbol,
        BigDecimal priorGuidanceLow,
        BigDecimal priorGuidanceHigh,
        BigDecimal newGuidanceLow,
        BigDecimal newGuidanceHigh,
        String metric
) {
    public GuidanceComparison {
        if (tickerSymbol == null || tickerSymbol.isBlank()) {
            throw new IllegalArgumentException("tickerSymbol must not be blank");
        }
        if (priorGuidanceLow == null) {
            throw new IllegalArgumentException("priorGuidanceLow must not be null");
        }
        if (priorGuidanceHigh == null) {
            throw new IllegalArgumentException("priorGuidanceHigh must not be null");
        }
        if (newGuidanceLow == null) {
            throw new IllegalArgumentException("newGuidanceLow must not be null");
        }
        if (newGuidanceHigh == null) {
            throw new IllegalArgumentException("newGuidanceHigh must not be null");
        }
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("metric must not be blank");
        }
    }
}
