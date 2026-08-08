package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing a detected contradiction between
 * a prior wiki claim and a newly harvested claim on the same topic.
 *
 * <p>Contradiction tracking is first-class in the wiki layer — reversals
 * and conflicts are structured data, not buried prose.  This enables the
 * future "Reversal Watch" newsletter section (Slice W3) and the wiki
 * linter (Slice W6) to surface regulatory U-turns, walked-back vendor
 * claims, and failed replications automatically.
 *
 * <p>Both the prior and new claims carry their own {@link SourceRef} lists
 * so provenance is traceable in both directions.
 *
 * @param pageSlug     slug of the wiki page where the contradiction was detected
 * @param priorClaim   the existing claim text that is contradicted
 * @param newClaim     the new claim text that contradicts the prior
 * @param priorSources provenance references supporting the prior claim
 * @param newSources   provenance references supporting the new claim
 * @param detectedAt   timestamp when the contradiction was flagged
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public record Contradiction(
        String pageSlug,
        String priorClaim,
        String newClaim,
        List<SourceRef> priorSources,
        List<SourceRef> newSources,
        Instant detectedAt
) {

    private static final String SLUG_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /**
     * Compact canonical constructor — validates required fields and
     * creates defensive copies of source lists.
     */
    public Contradiction {
        if (pageSlug == null || !pageSlug.matches(SLUG_PATTERN)) {
            throw new IllegalArgumentException(
                    "pageSlug must be non-null kebab-case, got: " + pageSlug);
        }
        if (priorClaim == null || priorClaim.isBlank()) {
            throw new IllegalArgumentException("priorClaim must not be blank");
        }
        if (newClaim == null || newClaim.isBlank()) {
            throw new IllegalArgumentException("newClaim must not be blank");
        }
        if (priorSources == null) {
            throw new IllegalArgumentException("priorSources must not be null");
        }
        if (newSources == null) {
            throw new IllegalArgumentException("newSources must not be null");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("detectedAt must not be null");
        }
        priorSources = List.copyOf(priorSources);
        newSources = List.copyOf(newSources);
    }
}
