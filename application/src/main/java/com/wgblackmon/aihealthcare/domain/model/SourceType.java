package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of a law source reference by provenance.
 *
 * <p>{@link #OFFICIAL} denotes a government or legislative body URL
 * (e.g. state legislature bill text). {@link #SECONDARY} denotes
 * third-party analysis, news coverage, or legal commentary.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public enum SourceType {

    /** Government or legislative body source (bill text, enrolled act). */
    OFFICIAL("Official"),

    /** Third-party analysis, news coverage, or legal commentary. */
    SECONDARY("Secondary");

    private final String displayLabel;

    SourceType(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    /**
     * Returns the human-readable label for UI display.
     */
    public String displayLabel() {
        return displayLabel;
    }
}
