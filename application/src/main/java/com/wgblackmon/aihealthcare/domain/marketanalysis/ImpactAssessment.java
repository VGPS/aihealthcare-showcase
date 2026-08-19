package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * Assessment of a market news item's impact along a single {@link ImpactDimension}.
 *
 * <p>The LLM classifier produces exactly five {@code ImpactAssessment} records per
 * {@link MarketDigestEntry} — one for each {@link ImpactDimension} value — each with
 * a direction and a one-sentence rationale.
 *
 * @param dimension  the impact dimension being assessed (required)
 * @param direction  whether the impact is positive, negative, or neutral (required)
 * @param rationale  one-sentence explanation of the assessed direction (required, non-blank)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record ImpactAssessment(
        ImpactDimension dimension,
        ImpactDirection direction,
        String rationale
) {

    public ImpactAssessment {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension must not be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
    }
}
