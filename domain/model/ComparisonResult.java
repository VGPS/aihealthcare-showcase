package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing a side-by-side comparison of multiple
 * prompt variants evaluated against the same set of source articles.
 *
 * <p>A comparison groups two or more {@link EvaluationResult} records that
 * share the same input articles, topic, and tone — differing only in the
 * prompt variant used.  This enables direct quality comparison to identify
 * the best-performing prompt template.
 *
 * <p>Both {@code articleIds} and {@code results} lists are defensively copied
 * and cannot be mutated after construction.
 *
 * @param comparisonId Unique identifier for this comparison (UUID).
 * @param articleIds   IDs of the shared source articles. Must not be empty.
 * @param topic        The topic used for all evaluations in this comparison.
 * @param tone         The tone used for all evaluations in this comparison.
 * @param results      Evaluation results, one per variant. Must contain at least 2.
 * @param comparedAt   Timestamp of when this comparison was performed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public record ComparisonResult(
        String                comparisonId,
        List<String>          articleIds,
        String                topic,
        NewsletterTone        tone,
        List<EvaluationResult> results,
        Instant               comparedAt
) {
    /** Compact canonical constructor — validates required fields and defensively copies lists. */
    public ComparisonResult {
        if (comparisonId == null || comparisonId.isBlank()) {
            throw new IllegalArgumentException("comparisonId must not be blank");
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
        if (results == null || results.size() < 2) {
            throw new IllegalArgumentException("results must contain at least two entries");
        }
        if (comparedAt == null) {
            throw new IllegalArgumentException("comparedAt must not be null");
        }
        // Defensive copies
        articleIds = List.copyOf(articleIds);
        results = List.copyOf(results);
    }
}
