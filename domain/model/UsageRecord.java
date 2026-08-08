package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record tracking a subscriber's AI query usage for a given month.
 *
 * <p>Each subscriber has at most one usage record per calendar month, identified by
 * the {@code yearMonth} string (format {@code "YYYY-MM"}).  The {@code queryCount}
 * tracks how many AI queries the subscriber has consumed, and {@code queryLimit}
 * records the maximum allowed for their tier during that month.
 *
 * <p>This record is the domain-facing view of usage data; persistence details are
 * handled by {@link com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort}
 * implementations.
 *
 * @param email      Subscriber email — natural key, must not be blank.
 * @param yearMonth  Month partition in {@code "YYYY-MM"} format (e.g. {@code "2026-05"}).
 * @param queryCount Number of AI queries consumed this month; always &ge; 0.
 * @param queryLimit Maximum allowed queries for this month; set from tier limits.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
public record UsageRecord(
        String email,
        String yearMonth,
        int    queryCount,
        int    queryLimit
) {
    /** Compact canonical constructor — validates required fields. */
    public UsageRecord {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (yearMonth == null || yearMonth.isBlank()) {
            throw new IllegalArgumentException("yearMonth must not be blank");
        }
        if (queryCount < 0) {
            throw new IllegalArgumentException("queryCount must not be negative");
        }
        if (queryLimit < 0) {
            throw new IllegalArgumentException("queryLimit must not be negative");
        }
    }

    /**
     * Returns {@code true} if the subscriber has remaining queries this month.
     *
     * @return whether {@code queryCount < queryLimit}
     */
    public boolean hasRemaining() {
        return queryCount < queryLimit;
    }
}
