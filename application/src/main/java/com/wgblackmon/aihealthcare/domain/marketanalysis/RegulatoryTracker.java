package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable snapshot of a single regulatory rulemaking or guidance action.
 *
 * <p>Trackers are upserted (keyed on {@code docketId + jurisdiction}) so the market
 * digest pipeline can detect stage transitions and approaching comment deadlines.
 * The {@code commentDeadline} is nullable — rules in ENFORCEMENT stage no longer
 * have an open comment window.
 *
 * @param jurisdiction    issuing regulatory body (required, non-null)
 * @param stage           current lifecycle stage (required, non-null)
 * @param docketId        official docket or reference number, e.g. "FDA-2024-N-2177"
 *                        (required, non-blank)
 * @param title           short human-readable description of the rule (required, non-blank)
 * @param commentDeadline deadline for public comments (nullable — absent after comment period)
 * @param lastUpdatedAt   timestamp of this snapshot record (required, non-null)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-07
 */
public record RegulatoryTracker(
        Jurisdiction jurisdiction,
        RulemakingStage stage,
        String docketId,
        String title,
        LocalDate commentDeadline,
        Instant lastUpdatedAt,
        String sourceUrl
) {
    public RegulatoryTracker {
        if (jurisdiction == null) {
            throw new IllegalArgumentException("jurisdiction must not be null");
        }
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        if (docketId == null || docketId.isBlank()) {
            throw new IllegalArgumentException("docketId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (lastUpdatedAt == null) {
            throw new IllegalArgumentException("lastUpdatedAt must not be null");
        }
    }
}
