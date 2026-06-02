package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing the complete result of an AI-enhanced
 * search — the retrieved articles plus one or more AI-generated syntheses
 * from different LLM models.
 *
 * <p>The {@code syntheses} list contains one entry per model that was queried
 * (e.g. Claude and GPT), enabling side-by-side comparison in the UI.
 * When no articles are found by the vector search, the syntheses list will
 * be empty.
 *
 * @param searchId   unique identifier for this search result
 * @param query      the original natural-language search query
 * @param articles   articles retrieved via vector similarity search
 * @param syntheses  AI-generated syntheses, one per model
 * @param searchedAt timestamp when the search was executed
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-02
 */
public record AiSearchResult(
        String searchId,
        String query,
        List<NewsArticle> articles,
        List<AiSearchSynthesis> syntheses,
        Instant searchedAt
) {
}
