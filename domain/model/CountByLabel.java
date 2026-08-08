package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing a single count-by-group entry.
 *
 * <p>Used as a breakdown element within analytics records such as
 * {@link IngestionAnalytics} — for example, "ACADEMIC: 142 articles" or
 * "PubMed AI Healthcare: 87 articles".  Both the label and count are
 * required and validated in the compact constructor.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record CountByLabel(String label, long count) {

    public CountByLabel {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be null or blank");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}
