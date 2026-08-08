package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing the quality scores assigned by the
 * AI evaluator to a single newsletter section.
 *
 * <p>Each dimension is scored on a [0.0, 1.0] scale where 1.0 is best:
 * <ul>
 *   <li>{@code relevance} — how well the summary reflects the source articles</li>
 *   <li>{@code conciseness} — brevity without loss of key information</li>
 *   <li>{@code attributionQuality} — correctness and completeness of source citations</li>
 *   <li>{@code toneMatch} — adherence to the requested {@link NewsletterTone}</li>
 *   <li>{@code completeness} — coverage of all major points from the source material</li>
 * </ul>
 *
 * <p>{@code overall} is a weighted average of the five dimensions, computed by
 * the AI evaluation adapter.  {@code scoringNotes} contains the evaluator's
 * free-text reasoning explaining the scores.
 *
 * @param relevance          How well the summary reflects the source articles.
 * @param conciseness        Brevity without loss of key information.
 * @param attributionQuality Correctness and completeness of source citations.
 * @param toneMatch          Adherence to the requested writing tone.
 * @param completeness       Coverage of all major points from the sources.
 * @param overall            Weighted average of the five dimensions.
 * @param scoringNotes       AI-generated reasoning explaining the scores.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public record EvaluationScore(
        double relevance,
        double conciseness,
        double attributionQuality,
        double toneMatch,
        double completeness,
        double overall,
        String scoringNotes
) {
    /** Compact canonical constructor — validates score ranges. */
    public EvaluationScore {
        if (relevance < 0.0 || relevance > 1.0) {
            throw new IllegalArgumentException("relevance must be in [0.0, 1.0]");
        }
        if (conciseness < 0.0 || conciseness > 1.0) {
            throw new IllegalArgumentException("conciseness must be in [0.0, 1.0]");
        }
        if (attributionQuality < 0.0 || attributionQuality > 1.0) {
            throw new IllegalArgumentException("attributionQuality must be in [0.0, 1.0]");
        }
        if (toneMatch < 0.0 || toneMatch > 1.0) {
            throw new IllegalArgumentException("toneMatch must be in [0.0, 1.0]");
        }
        if (completeness < 0.0 || completeness > 1.0) {
            throw new IllegalArgumentException("completeness must be in [0.0, 1.0]");
        }
        if (overall < 0.0 || overall > 1.0) {
            throw new IllegalArgumentException("overall must be in [0.0, 1.0]");
        }
    }
}
