package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record produced by the planning step of the staged research pipeline.
 *
 * <p>The {@link com.wgblackmon.aihealthcare.domain.service.ResearchPlanningService}
 * decomposes an original query into one or more focused {@code subQueries}.  Each
 * sub-query is independently resolved against the configured retrieval adapter before
 * the collected sources are forwarded to synthesis.
 *
 * <p>A single-element {@code subQueries} list indicates that no meaningful decomposition
 * was possible; the original query is used as-is.
 *
 * @param planId               Unique identifier for this plan (UUID).
 * @param originalQuery        The verbatim query string from the {@link ResearchRequest}.
 * @param subQueries           Ordered list of sub-queries derived from the original; never empty.
 * @param rationale            One-sentence explanation of the decomposition strategy.
 * @param estimatedSourceCount Expected total source count across all sub-queries.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record ResearchPlan(
        String planId,
        String originalQuery,
        List<String> subQueries,
        String rationale,
        int estimatedSourceCount
) {}
