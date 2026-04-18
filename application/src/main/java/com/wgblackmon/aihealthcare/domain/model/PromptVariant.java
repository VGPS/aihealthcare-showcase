package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a named prompt template variant for
 * AI summarization experimentation.
 *
 * <p>Prompt variants allow side-by-side comparison of different summarization
 * strategies.  Each variant stores the full prompt template text with
 * placeholders ({@code {topic}}, {@code {tone}}, {@code {articles}}) that
 * are substituted at evaluation time.  The {@code variantId} serves as the
 * unique business key across evaluations and comparisons.
 *
 * @param variantId    Stable identifier for this variant (e.g. "summarize-v2-concise").
 *                     Must not be blank.
 * @param name         Human-readable label for display. Must not be blank.
 * @param templateText The full prompt template with placeholders. Must not be blank.
 * @param description  Free-text description of what this variant experiments with.
 *                     May be blank.
 * @param createdAt    Timestamp of when this variant was created. Must not be null.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public record PromptVariant(
        String  variantId,
        String  name,
        String  templateText,
        String  description,
        Instant createdAt
) {
    /** Compact canonical constructor — validates required fields. */
    public PromptVariant {
        if (variantId == null || variantId.isBlank()) {
            throw new IllegalArgumentException("variantId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (templateText == null || templateText.isBlank()) {
            throw new IllegalArgumentException("templateText must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
