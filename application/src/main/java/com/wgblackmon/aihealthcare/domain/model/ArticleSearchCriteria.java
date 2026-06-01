package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable criteria record for multi-field article search.
 *
 * <p>All fields are optional (nullable). When a field is non-null it becomes
 * an AND-combined predicate in the search query. Text fields use
 * case-insensitive substring matching; {@code sourceTier} uses exact match;
 * date fields define inclusive range bounds.
 *
 * <p>The {@link #isEmpty()} convenience method returns {@code true} when every
 * field is null, allowing callers to short-circuit and avoid full-table scans.
 *
 * @param title         Substring match against article title (case-insensitive).
 * @param topic         Substring match against article topic (case-insensitive).
 * @param author        Substring match against article author (case-insensitive).
 * @param sourceName    Substring match against feed source label (case-insensitive).
 * @param sourceTier    Exact match against harvest tier (e.g. "ACADEMIC", "INDUSTRY").
 * @param bodyText      Substring match against article body text (case-insensitive).
 * @param publishedFrom Inclusive lower bound on {@code publishedAt}.
 * @param publishedTo   Inclusive upper bound on {@code publishedAt}.
 * @param createdFrom   Inclusive lower bound on {@code createdAt}.
 * @param createdTo     Inclusive upper bound on {@code createdAt}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-01
 * @updated 2026-06-01
 */
public record ArticleSearchCriteria(
        String title,
        String topic,
        String author,
        String sourceName,
        String sourceTier,
        String bodyText,
        Instant publishedFrom,
        Instant publishedTo,
        Instant createdFrom,
        Instant createdTo
) {

    /**
     * Returns {@code true} if every field is {@code null}, meaning no search
     * filters have been specified.
     *
     * @return {@code true} when all criteria fields are null
     */
    public boolean isEmpty() {
        return title == null
                && topic == null
                && author == null
                && sourceName == null
                && sourceTier == null
                && bodyText == null
                && publishedFrom == null
                && publishedTo == null
                && createdFrom == null
                && createdTo == null;
    }
}
