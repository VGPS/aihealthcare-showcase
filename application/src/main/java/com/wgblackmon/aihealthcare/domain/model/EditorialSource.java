package com.wgblackmon.aihealthcare.domain.model;

/**
 * A primary source citation attached to an editorial calendar item.
 *
 * <p>Carries the URL, human-readable label, and a loose source-type tag
 * (e.g. "government", "university", "industry_technical"). Source-type
 * is stored as a plain string so new source types require no schema change.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public record EditorialSource(
        String url,
        String label,
        String sourceType) {

    public EditorialSource {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("url must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
    }
}
