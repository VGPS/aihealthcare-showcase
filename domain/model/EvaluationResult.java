package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing the full result of evaluating a single
 * prompt variant against a set of source articles.
 *
 * <p>Captures which variant was used, which articles were input, the
 * AI-generated {@link NewsletterSection} output, and the {@link EvaluationScore}
 * assigned by the AI evaluator.  Results are persisted for historical review
 * and side-by-side comparison.
 *
 * <p>The {@code articleIds} list is defensively copied and cannot be mutated
 * after construction.
 *
 * @param evaluationId Unique identifier for this evaluation run (UUID).
 * @param variantId    The prompt variant that was evaluated.
 * @param variantName  Denormalized variant name for display convenience.
 * @param articleIds   IDs of the source articles used as input. Must not be empty.
 * @param topic        The topic used for summarization.
 * @param tone         The tone used for summarization.
 * @param section      The AI-generated newsletter section output.
 * @param score        The quality scores assigned by the AI evaluator.
 * @param evaluatedAt  Timestamp of when this evaluation was performed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public record EvaluationResult(
        String            evaluationId,
        String            variantId,
        String            variantName,
        List<String>      articleIds,
        String            topic,
        NewsletterTone    tone,
        NewsletterSection section,
        EvaluationScore   score,
        Instant           evaluatedAt
) {
    /** Compact canonical constructor — validates required fields and defensively copies lists. */
    public EvaluationResult {
        if (evaluationId == null || evaluationId.isBlank()) {
            throw new IllegalArgumentException("evaluationId must not be blank");
        }
        if (variantId == null || variantId.isBlank()) {
            throw new IllegalArgumentException("variantId must not be blank");
        }
        if (articleIds == null || articleIds.isEmpty()) {
            throw new IllegalArgumentException("articleIds must contain at least one entry");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (tone == null) {
            throw new IllegalArgumentException("tone must not be null");
        }
        if (section == null) {
            throw new IllegalArgumentException("section must not be null");
        }
        if (score == null) {
            throw new IllegalArgumentException("score must not be null");
        }
        if (evaluatedAt == null) {
            throw new IllegalArgumentException("evaluatedAt must not be null");
        }
        // Defensive copy
        articleIds = List.copyOf(articleIds);
    }
}
