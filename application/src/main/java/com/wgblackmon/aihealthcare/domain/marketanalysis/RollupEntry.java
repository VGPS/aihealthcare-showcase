package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.util.Objects;

/**
 * A de-duplicated entry within a {@link WeeklyRollup}.
 *
 * <p>When the same story resurfaces across multiple days in a week, all occurrences
 * are collapsed into a single {@link RollupEntry}. The {@link #representative} is
 * the occurrence with the best (lowest) {@link MarketImpactRank}, and
 * {@link #occurrenceCount} records how many times it appeared.
 *
 * @param representative  the occurrence with the best market-impact rank (non-null)
 * @param bestRank        lowest rank value seen across all occurrences (non-null)
 * @param factClassification most confident classification across all occurrences (non-null)
 * @param occurrenceCount how many times this story appeared across the week (≥ 1)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record RollupEntry(
        MarketDigestEntry representative,
        MarketImpactRank bestRank,
        FactClassification factClassification,
        int occurrenceCount
) {
    public RollupEntry {
        Objects.requireNonNull(representative, "representative is required");
        Objects.requireNonNull(bestRank, "bestRank is required");
        Objects.requireNonNull(factClassification, "factClassification is required");
        if (occurrenceCount < 1) {
            throw new IllegalArgumentException("occurrenceCount must be ≥ 1, got: " + occurrenceCount);
        }
    }
}
