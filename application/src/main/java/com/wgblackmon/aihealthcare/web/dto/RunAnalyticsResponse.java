package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;

/**
 * Web DTO for the GET /api/v1/analytics/runs response.
 *
 * <p>Maps from {@link com.wgblackmon.aihealthcare.domain.model.RunAnalytics}.
 * {@code mostRecentRunAt} is {@code null} when no runs exist yet.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record RunAnalyticsResponse(
        long totalRuns,
        long draftCount,
        long sentCount,
        long archivedCount,
        Instant mostRecentRunAt
) { }
