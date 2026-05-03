package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Web DTO for the GET /api/v1/analytics/ingestion response.
 *
 * <p>Maps from {@link com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record IngestionAnalyticsResponse(
        long totalArticles,
        List<CountByLabelResponse> bySourceTier,
        List<CountByLabelResponse> byTopic,
        long last7DaysCount,
        long last30DaysCount
) { }
