package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port — query the LLM-maintained knowledge wiki for
 * pages and contradictions.
 *
 * <p>This port exists so that newsletter drafting is fully decoupled
 * from the compilation process.  The newsletter service (and any
 * future consumer) queries the wiki through this interface without
 * knowing how pages are compiled, stored, or indexed.
 *
 * <p>Adapter implementations will live in {@code infrastructure.persistence}
 * (Slice W2), backed by pgvector for semantic similarity search.
 * Unit tests inject mock implementations so no persistence or AI
 * calls are made outside the appropriate Spring profiles.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public interface WikiQueryPort {

    /**
     * Find wiki pages relevant to the given natural-language query,
     * ranked by semantic similarity.
     *
     * @param query      the search query; must not be {@code null} or blank
     * @param maxResults maximum number of pages to return
     * @return matching pages ordered by relevance; empty list if none match
     */
    List<WikiPage> findRelevantPages(String query, int maxResults);

    /**
     * Retrieve a single wiki page by its unique slug.
     *
     * @param slug kebab-case page identifier; must not be {@code null}
     * @return the wiki page, or {@code null} if no page exists with that slug
     */
    WikiPage getPage(String slug);

    /**
     * Retrieve contradictions detected since the given timestamp,
     * ordered by detection time (most recent first).
     *
     * <p>This method powers the "Reversal Watch" newsletter section
     * (Slice W3): regulatory U-turns, walked-back vendor claims,
     * and failed replications surfaced automatically.
     *
     * @param since only return contradictions detected at or after this instant
     * @return contradictions ordered by {@code detectedAt} descending;
     *         empty list if none found
     */
    List<Contradiction> recentContradictions(Instant since);
}
