package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing aggregate statistics about
 * prompt evaluation runs.
 *
 * <p>Provides total evaluation and comparison counts, a per-variant
 * score summary list (see {@link VariantScore}), and the ID of the
 * best-performing variant as determined by the highest average overall
 * score.  {@code bestVariantId} is {@code null} when no evaluations
 * exist yet.
 *
 * <p>Note: the {@code bestVariantId} is computed by
 * {@link com.wgblackmon.aihealthcare.domain.service.AnalyticsService}
 * as a business-rule determination — the adapter layer does not set it.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record EvaluationAnalytics(
        long totalEvaluations,
        long totalComparisons,
        List<VariantScore> variantScores,
        String bestVariantId
) {

    public EvaluationAnalytics {
        if (variantScores == null) {
            throw new IllegalArgumentException("variantScores must not be null");
        }
    }
}
