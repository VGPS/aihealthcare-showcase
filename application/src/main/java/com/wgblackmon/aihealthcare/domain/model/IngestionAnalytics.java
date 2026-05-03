package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing aggregate statistics about
 * harvested articles stored in the database.
 *
 * <p>Provides a total article count, breakdowns by source tier
 * (ACADEMIC, REGULATORY, INDUSTRY, COMPETITOR, HUGGINGFACE) and by
 * topic name, plus counts for the last 7 and 30 calendar days based
 * on the {@code created_at} ingestion timestamp.
 *
 * <p>The {@code byTopic} list is ordered by count descending so that
 * the most active feed sources appear first.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record IngestionAnalytics(
        long totalArticles,
        List<CountByLabel> bySourceTier,
        List<CountByLabel> byTopic,
        long last7DaysCount,
        long last30DaysCount
) {

    public IngestionAnalytics {
        if (bySourceTier == null) {
            throw new IllegalArgumentException("bySourceTier must not be null");
        }
        if (byTopic == null) {
            throw new IllegalArgumentException("byTopic must not be null");
        }
    }
}
