package com.wgblackmon.aihealthcare.web.dto;

import java.math.BigDecimal;

/**
 * REST response record for a single guidance comparison.
 *
 * <p>Returned by {@code GET /api/market-digest/guidance/{ticker}?metric=}.
 * The prior and new ranges allow clients to compute beat/miss percentages
 * and display directional indicators.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record GuidanceComparisonResponse(
        String tickerSymbol,
        BigDecimal priorGuidanceLow,
        BigDecimal priorGuidanceHigh,
        BigDecimal newGuidanceLow,
        BigDecimal newGuidanceHigh,
        String metric
) {}
