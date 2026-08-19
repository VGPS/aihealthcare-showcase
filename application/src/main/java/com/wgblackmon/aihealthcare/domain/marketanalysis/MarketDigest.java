package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily market digest: the ordered, filtered, and ranked set of qualifying
 * {@link MarketDigestEntry} records produced by the digest pipeline for a single date.
 *
 * <p>An empty digest (zero qualifying entries) is still persisted for audit continuity —
 * use {@link #empty(LocalDate)} to construct one. The notifier is only called when
 * {@code entries} is non-empty.
 *
 * @param date         the digest date (required, non-null)
 * @param entries      qualifying entries sorted ascending by rank (required; null yields empty list)
 * @param generatedAt  timestamp when the pipeline completed (required, non-null)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record MarketDigest(
        LocalDate date,
        List<MarketDigestEntry> entries,
        Instant generatedAt
) {

    public MarketDigest {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * Creates an empty digest for the given date with the current timestamp.
     * Used when the pipeline finds no qualifying news items.
     *
     * @param date the digest date (must not be null)
     * @return a new {@code MarketDigest} with an empty entry list
     */
    public static MarketDigest empty(LocalDate date) {
        return new MarketDigest(date, List.of(), Instant.now());
    }
}
