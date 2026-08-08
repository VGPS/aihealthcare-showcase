package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a single persisted research pipeline execution.
 *
 * <p>A {@code ResearchRun} is created whenever
 * {@link com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase#conduct}
 * completes successfully.  It captures the essential metadata needed to build a
 * queryable history of all research conducted against the corpus:
 * what was asked, which pipeline ran, how many sources were found, and when.
 *
 * <p>The {@code runId} is the same UUID as {@link ResearchAnswer#answerId()} so
 * the two records are trivially joinable.
 *
 * @param runId          UUID — matches {@link ResearchAnswer#answerId()}.
 * @param query          The original research question submitted by the user.
 * @param mode           Pipeline mode name: {@code "LEGACY_GOOGLE"} or
 *                       {@code "STAGED_RESEARCH"}.
 * @param citationCount  Number of unique source citations assembled during this run.
 * @param researchedAt   Timestamp when the answer was generated.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
public record ResearchRun(
        String  runId,
        String  query,
        String  mode,
        int     citationCount,
        Instant researchedAt
) {}
