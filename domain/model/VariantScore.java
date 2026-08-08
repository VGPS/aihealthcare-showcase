package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing aggregate evaluation scores
 * for a single prompt variant.
 *
 * <p>All average score fields are in the range [0.0, 1.0] and represent
 * the mean of all individual {@link EvaluationScore} values recorded for
 * this variant.  Used as a element of {@link EvaluationAnalytics} to
 * support side-by-side variant performance comparison.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record VariantScore(
        String variantId,
        String variantName,
        long evaluationCount,
        double avgOverall,
        double avgRelevance,
        double avgConciseness,
        double avgCompleteness,
        double avgToneMatch,
        double avgAttributionQuality
) {

    public VariantScore {
        if (variantId == null || variantId.isBlank()) {
            throw new IllegalArgumentException("variantId must not be null or blank");
        }
        if (variantName == null || variantName.isBlank()) {
            throw new IllegalArgumentException("variantName must not be null or blank");
        }
    }
}
