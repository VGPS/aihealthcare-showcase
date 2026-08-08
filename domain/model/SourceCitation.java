package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a numbered, deduplicated citation assembled
 * from a set of {@link RetrievedSource} objects.
 *
 * <p>Citations are produced by
 * {@link com.wgblackmon.aihealthcare.domain.service.CitationAssembler}, which
 * deduplicates sources by URL and assigns sequential citation numbers starting at 1.
 * The resulting list is embedded in both the individual {@link ResearchSection} records
 * and the top-level {@link ResearchAnswer#allCitations()} collection.
 *
 * @param citationNumber  Sequential 1-based number assigned by {@code CitationAssembler}.
 * @param title           Title of the cited source.
 * @param url             Canonical URL of the cited source.
 * @param retrievedAt     When the source was retrieved; may be {@code null} for legacy sources.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record SourceCitation(
        int citationNumber,
        String title,
        String url,
        Instant retrievedAt
) {}
