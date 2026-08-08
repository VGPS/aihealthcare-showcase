package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable domain record representing a single keyword's trend signal
 * across three rolling time windows.
 *
 * <p>The momentum value is the ratio of the per-day frequency in the
 * recent window (last 30 days) to the per-day frequency in the previous
 * window (31–90 days).  A momentum > 1.5 indicates rising interest;
 * below 0.67 indicates fading interest.
 *
 * <p>Keywords with zero baseline in the 91–180 day window are classified
 * as {@link TrendDirection#NEW} regardless of momentum.
 *
 * @param keyword      the detected keyword or bigram phrase
 * @param current30d   occurrence count in the last 30 days
 * @param previous90d  occurrence count in the 31–90 day window
 * @param baseline180d occurrence count in the 91–180 day window
 * @param momentum     per-day frequency ratio: (current30d / 30) / (previous90d / 60)
 * @param direction    computed trend classification
 * @param firstSeenAt  earliest article timestamp containing this keyword
 * @param topArticles  highest-scoring articles for this keyword (may be empty)
 * @param summary      LLM-generated 2-3 sentence trend summary for analysts (nullable)
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-07-22
 * @updated 2026-07-27
 */
public record TrendSignal(
        String keyword,
        long current30d,
        long previous90d,
        long baseline180d,
        double momentum,
        TrendDirection direction,
        Instant firstSeenAt,
        List<ScoredArticle> topArticles,
        String summary
) {

    /**
     * Compact constructor — validates required fields and defensive-copies topArticles.
     */
    public TrendSignal {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        if (current30d < 0) {
            throw new IllegalArgumentException("current30d must not be negative");
        }
        if (previous90d < 0) {
            throw new IllegalArgumentException("previous90d must not be negative");
        }
        if (baseline180d < 0) {
            throw new IllegalArgumentException("baseline180d must not be negative");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        topArticles = topArticles == null ? List.of() : List.copyOf(topArticles);
    }

    /**
     * Convenience constructor without summary — defaults to null.
     */
    public TrendSignal(String keyword, long current30d, long previous90d,
                       long baseline180d, double momentum,
                       TrendDirection direction, Instant firstSeenAt,
                       List<ScoredArticle> topArticles) {
        this(keyword, current30d, previous90d, baseline180d,
             momentum, direction, firstSeenAt, topArticles, null);
    }

    /**
     * Convenience constructor without topArticles or summary — defaults to empty list and null.
     * Maintains backward compatibility with existing callers.
     */
    public TrendSignal(String keyword, long current30d, long previous90d,
                       long baseline180d, double momentum,
                       TrendDirection direction, Instant firstSeenAt) {
        this(keyword, current30d, previous90d, baseline180d,
             momentum, direction, firstSeenAt, List.of(), null);
    }
}
