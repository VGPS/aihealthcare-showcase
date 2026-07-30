package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing a point-in-time snapshot of detected
 * legal/regulatory trends.
 *
 * <p>Generated on a weekly schedule by the legal trend detection pipeline,
 * each snapshot captures rising trends across litigation, regulation, and
 * policy domains.
 *
 * @param generatedAt   when this snapshot was computed
 * @param windowDays    the primary window size (typically 30)
 * @param risingTrends  top trends by momentum descending
 * @param totalKeywords total unique keywords analyzed
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
public record LegalTrendSnapshot(
        Instant generatedAt,
        int windowDays,
        List<LegalTrendSignal> risingTrends,
        int totalKeywords
) {

    /** Compact constructor — validates required fields, creates defensive copy. */
    public LegalTrendSnapshot {
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
        if (windowDays < 1) {
            throw new IllegalArgumentException("windowDays must be >= 1");
        }
        if (risingTrends == null) {
            throw new IllegalArgumentException("risingTrends must not be null");
        }
        risingTrends = List.copyOf(risingTrends);
    }
}
