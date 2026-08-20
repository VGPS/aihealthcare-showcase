package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A 7-day market digest rollup aggregating and de-duplicating entries across all
 * daily digests from {@link #weekStart} through {@code weekStart + 6 days}.
 *
 * <p>Near-duplicate stories that resurface on multiple days within the week are
 * collapsed into a single {@link RollupEntry}. The rollup is generated on-demand
 * by {@link WeeklyRollupService} and is not persisted.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record WeeklyRollup(
        LocalDate weekStart,
        List<RollupEntry> entries,
        Instant generatedAt
) {
    public WeeklyRollup {
        Objects.requireNonNull(weekStart, "weekStart is required");
        Objects.requireNonNull(generatedAt, "generatedAt is required");
        entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }
}
