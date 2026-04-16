package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle status of a persisted {@link NewsletterRun}.
 *
 * <p>A run progresses from {@code DRAFT} (generated, not yet sent) through
 * {@code SENT} (delivered to subscribers) to {@code ARCHIVED} (retained for
 * historical reference only).  The subscriber archive page displays runs in
 * all three statuses; only {@code SENT} and {@code ARCHIVED} runs are shown
 * to subscribers by default.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public enum NewsletterRunStatus {

    /** Generated and stored but not yet delivered to subscribers. */
    DRAFT,

    /** Delivered to subscribers via email. */
    SENT,

    /** Retained for historical reference; no longer actively promoted. */
    ARCHIVED
}
