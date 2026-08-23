package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of why a pipeline step failed.
 *
 * <p>Used by {@link PipelineRunEvent} to surface actionable failure categories
 * on the admin pipeline dashboard.  NETWORK and LLM_QUOTA failures are retryable;
 * LLM_AUTH requires an API key fix; FATAL requires a JVM restart.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-23
 * @updated 2026-08-23
 */
public enum PipelineErrorType {

    /** HTTP 504/503, connection timeout, or network-layer failure — retryable after a delay. */
    NETWORK,

    /** LLM provider returned 429 (rate limited) or 402 (payment required) — needs funding or cooldown. */
    LLM_QUOTA,

    /** LLM provider returned 401 or 403 — API key invalid or expired. */
    LLM_AUTH,

    /** JVM-level error (OutOfMemoryError, StackOverflowError) — unrecoverable without restart. */
    FATAL,

    /** Exception did not match any known classification. */
    UNKNOWN
}
