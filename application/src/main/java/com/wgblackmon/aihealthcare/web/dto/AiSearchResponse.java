package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * REST response DTO for the AI-enhanced search endpoint.
 *
 * <p>Contains the search metadata, the number of articles retrieved from the
 * vector store, and one synthesis entry per LLM model that was queried.
 *
 * @param searchId        unique identifier for this search
 * @param query           the original search query
 * @param articleCount    number of articles retrieved from the vector store
 * @param syntheses       AI syntheses, one per model that found a relevant match (e.g. Claude, GPT)
 * @param noMatchModels   names of models that were queried but reported no relevant match
 * @param searchedAt      timestamp when the search was executed
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-06-02
 * @updated 2026-08-25
 */
public record AiSearchResponse(
        String searchId,
        String query,
        int articleCount,
        List<AiSearchSynthesisDto> syntheses,
        List<String> noMatchModels,
        Instant searchedAt
) {
}
