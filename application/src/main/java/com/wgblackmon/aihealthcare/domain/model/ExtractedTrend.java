package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing a single trend topic extracted by an LLM
 * from a batch of article titles.
 *
 * <p>Unlike the n-gram frequency approach, this record captures semantically
 * meaningful themes identified by an LLM — e.g. "FDA AI Device Clearances"
 * rather than "ai device" — along with a one-sentence description and the
 * indices of articles that relate to this theme.
 *
 * @param label          short 2-5 word theme label (e.g. "Radiology AI Adoption")
 * @param description    one-sentence summary of why this is a notable trend
 * @param articleIndices zero-based indices into the original numbered article list
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
public record ExtractedTrend(
        String label,
        String description,
        List<Integer> articleIndices
) {

    /**
     * Compact constructor — validates required fields and defensive-copies articleIndices.
     */
    public ExtractedTrend {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        articleIndices = articleIndices == null ? List.of() : List.copyOf(articleIndices);
    }
}
