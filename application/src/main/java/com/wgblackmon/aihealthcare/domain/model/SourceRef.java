package com.wgblackmon.aihealthcare.domain.model;

import java.time.LocalDate;

/**
 * Immutable provenance record linking a single wiki claim back to the
 * harvested {@link NewsArticle} that supports it.
 *
 * <p>Provenance is first-class in the wiki layer: every factual claim
 * must trace to at least one {@code SourceRef} so readers (and the
 * LLM linter in future slices) can verify accuracy against the
 * original source material.  The wiki layer never mutates the
 * underlying {@code NewsArticle} records.
 *
 * @param articleId   stable identifier referencing {@link NewsArticle#articleId()}
 * @param sourceName  human-readable origin label (e.g. "FDA", "PubMed")
 * @param harvestedOn date the source article was harvested into the system
 * @param excerpt     short supporting snippet from the article; {@code null}
 *                    when no specific excerpt is relevant
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public record SourceRef(
        String articleId,
        String sourceName,
        LocalDate harvestedOn,
        String excerpt
) {
    /**
     * Compact canonical constructor — validates required fields.
     */
    public SourceRef {
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId must not be blank");
        }
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (harvestedOn == null) {
            throw new IllegalArgumentException("harvestedOn must not be null");
        }
    }
}
