package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

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
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public record TrendSignal(
        String keyword,
        long current30d,
        long previous90d,
        long baseline180d,
        double momentum,
        TrendDirection direction,
        Instant firstSeenAt
) {

    /**
     * Compact constructor — validates required fields.
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
    }
}
