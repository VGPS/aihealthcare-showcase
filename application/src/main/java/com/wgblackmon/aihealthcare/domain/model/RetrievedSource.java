package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a single source document returned by a
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort} adapter.
 *
 * <p>Adapters convert their native source representation (e.g., a
 * {@link com.wgblackmon.aihealthcare.domain.model.NewsArticle} from the ingestion DB,
 * or a Perplexity Sonar search result) into this uniform record before returning it to
 * the orchestrator.  This isolates the pipeline from backend-specific data shapes.
 *
 * <p>{@code snippet} carries the most relevant excerpt or description of the source.
 * For article-backed sources it is populated from {@code bodyText}; for search-API
 * sources it is the passage excerpt returned by the search engine.
 *
 * @param sourceId    Stable identifier for deduplication (article ID, model ID, or URL hash).
 * @param title       Title or headline of the source document.
 * @param url         Canonical URL of the source; used in citations.
 * @param snippet     Relevant text excerpt or description; may be blank for some backends.
 * @param engine      Name of the retrieval backend that produced this source.
 * @param retrievedAt Timestamp when this source was retrieved; never {@code null}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record RetrievedSource(
        String sourceId,
        String title,
        String url,
        String snippet,
        String engine,
        Instant retrievedAt
) {}
