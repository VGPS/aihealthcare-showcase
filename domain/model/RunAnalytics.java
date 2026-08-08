package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing aggregate statistics about
 * persisted newsletter runs.
 *
 * <p>Tracks total run count broken down by {@link NewsletterRunStatus}
 * (DRAFT, SENT, ARCHIVED) and the timestamp of the most recently
 * generated run.  {@code mostRecentRunAt} is {@code null} when no
 * runs exist yet.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record RunAnalytics(
        long totalRuns,
        long draftCount,
        long sentCount,
        long archivedCount,
        Instant mostRecentRunAt
) { }
