package com.wgblackmon.aihealthcare.web.dto;

/**
 * Web DTO representing aggregate evaluation scores for a single prompt variant.
 *
 * <p>Maps from {@link com.wgblackmon.aihealthcare.domain.model.VariantScore}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record VariantScoreResponse(
        String variantId,
        String variantName,
        long evaluationCount,
        double avgOverall,
        double avgRelevance,
        double avgConciseness,
        double avgCompleteness,
        double avgToneMatch,
        double avgAttributionQuality
) { }
