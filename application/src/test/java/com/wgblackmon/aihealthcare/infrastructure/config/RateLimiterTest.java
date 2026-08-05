package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimiter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(5);
    }

    @Test
    void tryAcquire_underLimit_returnsAllowed() {
        RateLimitResult result = rateLimiter.tryAcquire("key1");

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingRequests()).isEqualTo(4);
        assertThat(result.retryAfterMillis()).isEqualTo(0);
    }

    @Test
    void tryAcquire_atLimit_returnsDenied() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("key1");
        }

        RateLimitResult result = rateLimiter.tryAcquire("key1");

        assertThat(result.allowed()).isFalse();
        assertThat(result.remainingRequests()).isEqualTo(0);
        assertThat(result.retryAfterMillis()).isGreaterThan(0);
    }

    @Test
    void tryAcquire_differentKeys_independentLimits() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("key1");
        }

        RateLimitResult key1Result = rateLimiter.tryAcquire("key1");
        RateLimitResult key2Result = rateLimiter.tryAcquire("key2");

        assertThat(key1Result.allowed()).isFalse();
        assertThat(key2Result.allowed()).isTrue();
    }

    @Test
    void tryAcquire_remainingDecrementsCorrectly() {
        RateLimitResult r1 = rateLimiter.tryAcquire("key1");
        RateLimitResult r2 = rateLimiter.tryAcquire("key1");
        RateLimitResult r3 = rateLimiter.tryAcquire("key1");

        assertThat(r1.remainingRequests()).isEqualTo(4);
        assertThat(r2.remainingRequests()).isEqualTo(3);
        assertThat(r3.remainingRequests()).isEqualTo(2);
    }

    @Test
    void evictStaleEntries_keepsRecentEntries() {
        rateLimiter.tryAcquire("key1");
        rateLimiter.tryAcquire("key1");

        rateLimiter.evictStaleEntries();

        RateLimitResult result = rateLimiter.tryAcquire("key1");
        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingRequests()).isEqualTo(2);
    }
}
