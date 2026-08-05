package com.wgblackmon.aihealthcare.domain.model;

/**
 * Result of a rate limit check for an API key request.
 *
 * <p>If {@code allowed} is {@code false}, the caller should return HTTP 429
 * with the {@code retryAfterMillis} value in the {@code Retry-After} header.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record RateLimitResult(boolean allowed, int remainingRequests, long retryAfterMillis) {

    public RateLimitResult {
        if (remainingRequests < 0) {
            remainingRequests = 0;
        }
        if (retryAfterMillis < 0) {
            retryAfterMillis = 0;
        }
    }
}
