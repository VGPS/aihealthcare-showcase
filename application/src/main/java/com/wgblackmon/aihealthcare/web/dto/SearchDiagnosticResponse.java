package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Response record for the search diagnostic endpoint that compares three
 * search strategies: trend snapshot lookup, semantic (vector) search,
 * and text (LIKE) search.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-01
 * @updated 2026-08-01
 */
public record SearchDiagnosticResponse(
        String query,
        int topK,
        SnapshotResult trendSnapshotLookup,
        SearchResult semanticSearch,
        SearchResult textSearch
) {

    /**
     * Result of scanning the TrendSnapshot for articles matching the query title.
     */
    public record SnapshotResult(
            boolean found,
            String matchedKeyword,
            List<ArticleHit> matches
    ) {}

    /**
     * Result of a search strategy (semantic or text).
     */
    public record SearchResult(
            int resultCount,
            List<ArticleHit> results
    ) {}

    /**
     * A single article hit with rank and identifying fields.
     */
    public record ArticleHit(
            int rank,
            String articleId,
            String title,
            String topic,
            String sourceName,
            String url
    ) {}
}
