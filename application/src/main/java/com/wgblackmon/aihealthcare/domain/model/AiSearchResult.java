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
 * <p>A model that was queried but judged the retrieved articles irrelevant
 * reports {@code NO_MATCH} and is silently excluded from {@code syntheses} —
 * its name is recorded in {@code noMatchModelNames} instead, so the UI can
 * distinguish "this model correctly found nothing relevant" from "this model
 * wasn't queried at all."
 *
 * @param searchId         unique identifier for this search result
 * @param query            the original natural-language search query
 * @param articles         articles retrieved via vector similarity search
 * @param syntheses        AI-generated syntheses, one per model that found a relevant match
 * @param noMatchModelNames names of models that were queried but reported NO_MATCH
 * @param searchedAt       timestamp when the search was executed
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-06-02
 * @updated 2026-08-25
 */
public record AiSearchResult(
        String searchId,
        String query,
        List<NewsArticle> articles,
        List<AiSearchSynthesis> syntheses,
        List<String> noMatchModelNames,
        Instant searchedAt
) {
}
