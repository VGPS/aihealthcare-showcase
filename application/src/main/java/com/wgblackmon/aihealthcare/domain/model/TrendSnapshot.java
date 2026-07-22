package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing a point-in-time snapshot of detected
 * keyword trends across the article corpus.
 *
 * <p>Generated weekly by {@code TrendDetectionService}, each snapshot captures
 * the top rising, fading, and newly emerged keywords based on frequency
 * analysis across rolling time windows.
 *
 * @param generatedAt   when this snapshot was computed
 * @param windowDays    the primary window size (typically 30)
 * @param risingTopics  top keywords by momentum descending
 * @param fadingTopics  bottom keywords by momentum ascending
 * @param newTopics     keywords with zero baseline (91–180 day window)
 * @param totalKeywords total unique keywords analyzed
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public record TrendSnapshot(
        Instant generatedAt,
        int windowDays,
        List<TrendSignal> risingTopics,
        List<TrendSignal> fadingTopics,
        List<TrendSignal> newTopics,
        int totalKeywords
) {

    /**
     * Compact constructor — validates required fields, creates defensive copies.
     */
    public TrendSnapshot {
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
        if (windowDays < 1) {
            throw new IllegalArgumentException("windowDays must be >= 1");
        }
        if (risingTopics == null) {
            throw new IllegalArgumentException("risingTopics must not be null");
        }
        if (fadingTopics == null) {
            throw new IllegalArgumentException("fadingTopics must not be null");
        }
        if (newTopics == null) {
            throw new IllegalArgumentException("newTopics must not be null");
        }
        risingTopics = List.copyOf(risingTopics);
        fadingTopics = List.copyOf(fadingTopics);
        newTopics = List.copyOf(newTopics);
    }
}
