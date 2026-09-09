package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle states of an enterprise data job.
 *
 * <p>Terminal states ({@code SUCCEEDED}, {@code FAILED}, {@code CANCELLED},
 * {@code EXPIRED}) cannot transition further. The reaper marks orphaned
 * {@code RUNNING} jobs as {@code FAILED}; the retention sweeper marks
 * past-expiry jobs as {@code EXPIRED}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public enum DataJobStatus {

    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED;

    /**
     * Returns {@code true} if no further state transition is possible.
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
