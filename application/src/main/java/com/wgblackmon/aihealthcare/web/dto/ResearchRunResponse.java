package com.wgblackmon.aihealthcare.web.dto;

/**
 * Immutable response DTO for a single persisted research pipeline execution.
 *
 * <p>Returned by {@code GET /api/v1/research/runs} (list) and
 * {@code GET /api/v1/research/runs/{runId}} (single lookup).
 *
 * @param runId         UUID — matches the {@code answerId} of the originating answer.
 * @param query         The original research question submitted by the user.
 * @param mode          Pipeline mode: {@code "LEGACY_GOOGLE"} or {@code "STAGED_RESEARCH"}.
 * @param citationCount Number of unique source citations assembled during this run.
 * @param researchedAt  ISO-8601 timestamp string when the answer was generated.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
public record ResearchRunResponse(
        String runId,
        String query,
        String mode,
        int    citationCount,
        String researchedAt
) {}
