package com.wgblackmon.aihealthcare.infrastructure.summary;

import java.time.LocalDate;

/**
 * One piece of normalized source material, assigned a stable short ID (S1, S2, ...)
 * so the extraction model can cite it precisely.
 *
 * <p>All source types — DB topic-summary rows, harvested NewsArticle records,
 * wiki pages, Perplexity research output — are normalized into this shape before
 * being passed to the extraction stage.  The {@code citeId} is assigned per summary
 * run and is not persisted.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
public record SourceDoc(
        String    citeId,       // "S1", "S2", ... assigned per run
        SourceType type,
        String    title,
        String    url,          // nullable for DB rows
        String    dbRef,        // e.g. "topic:anthropic-healthcare"; nullable
        LocalDate publishedOn,  // nullable
        String    content       // trimmed text passed to the model
) {
    public enum SourceType {
        PERPLEXITY_RESEARCH,
        TOPIC_SUMMARY,
        NEWS_ARTICLE,
        WIKI,
        DB_ROW,
        VECTOR_CHUNK
    }
}
