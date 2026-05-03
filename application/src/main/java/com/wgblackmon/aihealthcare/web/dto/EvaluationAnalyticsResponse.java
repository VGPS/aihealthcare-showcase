package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Web DTO for the GET /api/v1/analytics/evaluations response.
 *
 * <p>Maps from {@link com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics}.
 * {@code bestVariantId} is {@code null} when no evaluations exist.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record EvaluationAnalyticsResponse(
        long totalEvaluations,
        long totalComparisons,
        List<VariantScoreResponse> variantScores,
        String bestVariantId
) { }
