package com.wgblackmon.aihealthcare.infrastructure.summary;

import java.util.List;

/**
 * Structured extraction result from Stage 1 of the anti-slop summary pipeline.
 *
 * <p>Every field in this record corresponds to a section of the extraction prompt.
 * The extraction model populates this from source documents only — no invented facts.
 * The writer model (Stage 2) receives ONLY this record, never the raw sources,
 * which forces every prose claim to trace back to an extracted finding with a
 * cited source ID.
 *
 * <p>Instances are produced by {@link ExtractionService} and consumed by
 * {@link SummaryWriter}.  They are also serialized to JSON and stored in the
 * before/after report for inspection.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
public record SummaryExtraction(
        String         headline,
        List<Finding>  findings,
        List<KeyNumber> keyNumbers,
        List<Entity>   entities,
        List<Conflict> conflicts,
        List<String>   unknowns,
        String         soWhat
) {
    public enum Confidence { HIGH, MEDIUM, LOW }

    /** One concrete extracted fact with mandatory source citations. */
    public record Finding(
            String         statement,
            List<String>   sourceIds,
            Confidence     confidence,
            String         asOf      // nullable ISO date
    ) {}

    /** A specific number pulled from a source (dollar amount, percentage, count, date). */
    public record KeyNumber(
            String       label,
            String       value,
            String       unit,       // nullable
            List<String> sourceIds
    ) {}

    /** A named organization, product, regulator, or person central to the findings. */
    public record Entity(
            String name,
            String kind,  // VENDOR | PROVIDER | PAYER | REGULATOR | PRODUCT | PERSON | OTHER
            String role
    ) {}

    /** Two conflicting claims from different sources on the same topic. */
    public record Conflict(
            String       topic,
            String       positionA,
            List<String> sourcesA,
            String       positionB,
            List<String> sourcesB
    ) {}
}
