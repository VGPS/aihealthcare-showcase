package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * REST response DTO for the AI-enhanced search endpoint.
 *
 * <p>Contains the search metadata, the number of articles retrieved from the
 * vector store, and one synthesis entry per LLM model that was queried.
 *
 * @param searchId     unique identifier for this search
 * @param query        the original search query
 * @param articleCount number of articles retrieved from the vector store
 * @param syntheses    AI syntheses, one per model (e.g. Claude, GPT)
 * @param searchedAt   timestamp when the search was executed
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-02
 */
public record AiSearchResponse(
        String searchId,
        String query,
        int articleCount,
        List<AiSearchSynthesisDto> syntheses,
        Instant searchedAt
) {
}
