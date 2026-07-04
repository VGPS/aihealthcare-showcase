package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Response DTO for a PubMed backfill harvest operation.
 *
 * <p>Reports per-query article counts and total articles harvested.
 *
 * @param queriesExecuted   number of PubMed queries run
 * @param totalArticles     total articles harvested across all queries
 * @param queryResults      per-query breakdown (query string + count)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public record BackfillResultResponse(
        int queriesExecuted,
        int totalArticles,
        List<QueryResult> queryResults
) {

    /**
     * Per-query result showing the search term and number of articles found.
     *
     * @param query         the PubMed search term
     * @param articlesFound number of articles harvested for this query
     */
    public record QueryResult(String query, int articlesFound) {}
}
