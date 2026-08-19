package com.wgblackmon.aihealthcare.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Response body for {@code GET /api/market-digest/weekly-rollup?weekOf=}.
 *
 * <p>Aggregates all market digests for the 7-day window starting on {@code weekOf}
 * (Monday) through {@code weekEnd} (Sunday), returning per-category counts and
 * per-day summaries.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record WeeklyRollupResponse(
        LocalDate weekOf,
        LocalDate weekEnd,
        int totalQualifyingEntries,
        List<MarketDigestSummary> dailySummaries,
        List<CategoryCount> byCategory
) {

    /**
     * Per-category count for the rollup period.
     */
    public record CategoryCount(String category, int count) {}
}
