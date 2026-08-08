package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing a single legal/regulatory trend signal
 * detected from article frequency analysis.
 *
 * <p>Similar to {@link TrendSignal} but carries a {@code category} field
 * (LITIGATION, REGULATION, POLICY) to classify the source of the trend.
 * Momentum is computed as the ratio of per-day frequency between recent
 * and prior time windows.
 *
 * @param keyword      the detected keyword or phrase
 * @param category     legal category: "LITIGATION", "REGULATION", or "POLICY"
 * @param current30d   occurrence count in the last 30 days
 * @param previous90d  occurrence count in the 31-90 day window
 * @param momentum     per-day frequency ratio: (current30d / 30) / (previous90d / 60)
 * @param direction    computed trend classification (RISING, NEW, STABLE, FADING)
 * @param topArticleIds IDs of the most relevant articles for this trend
 * @param summary      LLM-generated trend summary for analysts (nullable)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
public record LegalTrendSignal(
        String keyword,
        String category,
        long current30d,
        long previous90d,
        double momentum,
        TrendDirection direction,
        List<String> topArticleIds,
        String summary
) {

    /** Compact constructor — validates required fields and defensive-copies lists. */
    public LegalTrendSignal {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (current30d < 0) {
            throw new IllegalArgumentException("current30d must not be negative");
        }
        if (previous90d < 0) {
            throw new IllegalArgumentException("previous90d must not be negative");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        topArticleIds = topArticleIds == null ? List.of() : List.copyOf(topArticleIds);
    }

    /**
     * Convenience constructor without summary — defaults to null.
     */
    public LegalTrendSignal(String keyword, String category, long current30d,
                            long previous90d, double momentum,
                            TrendDirection direction, List<String> topArticleIds) {
        this(keyword, category, current30d, previous90d, momentum, direction, topArticleIds, null);
    }
}
