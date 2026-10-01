package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle status for a saved social post draft.
 *
 * <p>Transitions: DRAFT → POSTED or DRAFT → ARCHIVED.
 * A POSTED or ARCHIVED post is terminal — it cannot revert to DRAFT.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public enum SavedPostStatus {
    DRAFT,
    POSTED,
    ARCHIVED;

    public boolean isTerminal() {
        return this == POSTED || this == ARCHIVED;
    }
}
