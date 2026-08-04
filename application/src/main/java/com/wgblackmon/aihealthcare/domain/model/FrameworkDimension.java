package com.wgblackmon.aihealthcare.domain.model;

/**
 * A single scored dimension within a healthcare framework competitive analysis.
 *
 * <p>Each dimension captures one axis of evaluation (e.g. "Technical Maturity",
 * "Regulatory Positioning") with a 1–10 score and a brief rationale derived
 * from article evidence.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public record FrameworkDimension(
        String name,
        int score,
        String rationale
) {

    /**
     * Compact constructor — validates score range and required fields.
     */
    public FrameworkDimension {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Dimension name must not be blank");
        }
        if (score < 1 || score > 10) {
            throw new IllegalArgumentException("Score must be between 1 and 10, got: " + score);
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("Rationale must not be blank");
        }
    }
}
