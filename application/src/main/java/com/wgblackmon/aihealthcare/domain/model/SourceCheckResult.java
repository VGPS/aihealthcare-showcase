package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Result of checking a single law source URL for content changes.
 *
 * <p>Returned by the source monitor when it fetches a URL and computes a
 * SHA-256 hash of the response body. The {@code changed} flag is true when
 * the new hash differs from the previously stored hash, or when the URL
 * returns an error status that differs from the last known status.
 *
 * @param httpStatus   HTTP status code from the fetch attempt
 * @param contentHash  SHA-256 hex digest of the fetched body (null if fetch failed)
 * @param changed      true if content hash differs from stored hash
 * @param fetchedAt    timestamp of the fetch attempt
 * @param errorMessage human-readable error message (null if fetch succeeded)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
public record SourceCheckResult(
        int httpStatus,
        String contentHash,
        boolean changed,
        Instant fetchedAt,
        String errorMessage
) {

    /**
     * Compact constructor — validates fetchedAt.
     */
    public SourceCheckResult {
        if (fetchedAt == null) {
            throw new IllegalArgumentException("fetchedAt must not be null");
        }
    }
}
