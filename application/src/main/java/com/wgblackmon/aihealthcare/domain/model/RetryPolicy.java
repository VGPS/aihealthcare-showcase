package com.wgblackmon.aihealthcare.domain.model;

/**
 * Bounded-retry configuration for {@link com.wgblackmon.aihealthcare.domain.service.ResilientStepExecutor}.
 *
 * <p>Ported from the {@code RetryPolicy} record used by the url-shortener prototype's
 * agentic SDLC orchestration layer ({@code PhaseExecutor}).
 *
 * @param maxAttempts       Number of retries after the first attempt (0 = no retry).
 * @param initialBackoffMs  Backoff duration before the first retry, in milliseconds.
 * @param backoffMultiplier Multiplier applied to the backoff on each subsequent retry.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
public record RetryPolicy(int maxAttempts, long initialBackoffMs, double backoffMultiplier) {

    public RetryPolicy {
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("maxAttempts must be >= 0");
        }
        if (initialBackoffMs < 0) {
            throw new IllegalArgumentException("initialBackoffMs must be >= 0");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        }
    }

    /** Default policy for research retrieval/synthesis calls: 2 retries, 1s initial backoff, 2x multiplier. */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(2, 1_000, 2.0);
    }

    /** Zero-retry policy — the first attempt is the only attempt. */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0, 1.0);
    }

    /** Backoff duration in milliseconds for the given retry number (0-based). */
    public long backoffForAttempt(int retryNumber) {
        double factor = Math.pow(backoffMultiplier, retryNumber);
        return (long) (initialBackoffMs * factor);
    }
}
