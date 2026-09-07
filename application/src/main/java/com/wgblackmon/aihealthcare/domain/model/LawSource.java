package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * A source reference attached to a {@link StateLaw}, tracking where the
 * law text or analysis was obtained and whether the source content has
 * changed since the last review.
 *
 * <p>The source monitor periodically re-fetches each URL, computes a
 * content hash, and sets {@code changedSinceLastReview} when drift is
 * detected. This enables the admin to review stale or updated sources.
 *
 * @param sourceType           official (government) or secondary (analysis/news)
 * @param url                  the source URL
 * @param lastFetchedAt        when the URL was last successfully fetched (nullable)
 * @param lastContentHash      SHA-256 hash of the last fetched content (nullable)
 * @param lastHttpStatus       HTTP status code of the last fetch attempt (nullable)
 * @param changedSinceLastReview true if content hash changed since last admin review
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public record LawSource(
        SourceType sourceType,
        String url,
        Instant lastFetchedAt,
        String lastContentHash,
        Integer lastHttpStatus,
        boolean changedSinceLastReview
) {

    /**
     * Compact constructor — validates required fields.
     */
    public LawSource {
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType must not be null");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
    }
}
