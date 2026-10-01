package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle state for an editorial calendar item.
 *
 * <p>State machine:
 * <pre>
 * PLANNED → RESEARCHING → DRAFTING → REVIEW → SCHEDULED → PUBLISHED
 *                                                              ↓
 *                                                         NEEDS_UPDATE
 *                                                              ↓
 *                                                         RESEARCHING
 * </pre>
 *
 * <p>PUBLISHED means the corresponding static HTML file has been written to
 * {@code static/insights/}. NEEDS_UPDATE is triggered when underlying
 * regulation, court order, or annual thresholds change.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public enum EditorialStatus {
    PLANNED,
    RESEARCHING,
    DRAFTING,
    REVIEW,
    SCHEDULED,
    PUBLISHED,
    NEEDS_UPDATE;

    /**
     * Returns the next status in the lifecycle progression.
     * PUBLISHED advances to NEEDS_UPDATE; NEEDS_UPDATE loops back to RESEARCHING.
     */
    public EditorialStatus next() {
        return switch (this) {
            case PLANNED      -> RESEARCHING;
            case RESEARCHING  -> DRAFTING;
            case DRAFTING     -> REVIEW;
            case REVIEW       -> SCHEDULED;
            case SCHEDULED    -> PUBLISHED;
            case PUBLISHED    -> NEEDS_UPDATE;
            case NEEDS_UPDATE -> RESEARCHING;
        };
    }
}
