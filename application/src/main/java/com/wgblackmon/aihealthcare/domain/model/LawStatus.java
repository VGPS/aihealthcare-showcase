package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle status of a state health-AI law in the legislation registry.
 *
 * <p>Laws progress through legislative stages; NOT_ENACTED entries exist
 * in the registry solely for deduplication suppression (preventing the
 * discovery pipeline from re-surfacing defeated bills).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public enum LawStatus {

    /** Signed into law and in effect (or awaiting effective date). */
    ENACTED("Enacted"),

    /** Enacted but enforcement is currently stayed by court order. */
    ENACTED_STAYED("Enacted (Stayed)"),

    /** Bill was defeated, vetoed, or expired — kept for dedup suppression. */
    NOT_ENACTED("Not Enacted"),

    /** Bill is still moving through the legislative process. */
    PENDING("Pending");

    private final String displayLabel;

    LawStatus(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    /**
     * Returns the human-readable label for UI display.
     */
    public String displayLabel() {
        return displayLabel;
    }
}
