package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing an AI model's synthesized response
 * to a search query, based on a set of retrieved articles.
 *
 * <p>Each synthesis is produced by a single LLM (identified by {@code modelName})
 * and contains a free-text summary plus a list of extracted key findings.
 * Multiple syntheses from different models are collected into an
 * {@link AiSearchResult} for side-by-side comparison.
 *
 * @param modelName   human-readable model identifier (e.g. "Claude", "GPT")
 * @param summary     3–5 sentence AI-generated synthesis of the retrieved articles
 * @param keyFindings bullet-point key findings extracted by the model; may be empty
 * @param generatedAt timestamp when the synthesis was produced
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-02
 */
public record AiSearchSynthesis(
        String modelName,
        String summary,
        List<String> keyFindings,
        Instant generatedAt
) {
}
