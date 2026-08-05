package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.RateLimitResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window rate limiter for API key requests.
 *
 * <p>Tracks request timestamps per key identifier (typically API key hash)
 * using a one-minute sliding window. Thread-safe via synchronized blocks
 * on per-key deques.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final long STALE_MILLIS = 300_000L;

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequestsPerMinute) {
        log.debug("RateLimiter() | maxRequestsPerMinute={}", maxRequestsPerMinute);
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    /**
     * Attempts to acquire a rate limit token for the given key identifier.
     *
     * @param keyIdentifier the API key hash or other unique identifier
     * @return the rate limit result indicating whether the request is allowed
     */
    public RateLimitResult tryAcquire(String keyIdentifier) {
        log.debug("tryAcquire() | keyIdentifier=[REDACTED]");

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MILLIS;

        Deque<Long> timestamps = windows.computeIfAbsent(keyIdentifier, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            int currentCount = timestamps.size();
            if (currentCount < maxRequestsPerMinute) {
                timestamps.addLast(now);
                int remaining = maxRequestsPerMinute - currentCount - 1;
                RateLimitResult result = new RateLimitResult(true, remaining, 0);
                log.debug("tryAcquire() | return={}", result);
                return result;
            } else {
                long oldestInWindow = timestamps.peekFirst();
                long retryAfter = oldestInWindow + WINDOW_MILLIS - now;
                RateLimitResult result = new RateLimitResult(false, 0, retryAfter);
                log.debug("tryAcquire() | return={}", result);
                return result;
            }
        }
    }

    /**
     * Removes stale entries that have had no requests in the last 5 minutes.
     */
    public void evictStaleEntries() {
        log.debug("evictStaleEntries() | windowCount={}", windows.size());

        long cutoff = System.currentTimeMillis() - STALE_MILLIS;
        List<String> staleKeys = new ArrayList<>();

        for (var entry : windows.entrySet()) {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                if (timestamps.isEmpty() || timestamps.peekLast() < cutoff) {
                    staleKeys.add(entry.getKey());
                }
            }
        }

        for (String key : staleKeys) {
            windows.remove(key);
        }

        log.debug("evictStaleEntries() | evicted={}", staleKeys.size());
    }
}
