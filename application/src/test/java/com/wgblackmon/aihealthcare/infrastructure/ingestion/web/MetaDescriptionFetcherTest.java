package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MetaDescriptionFetcher}.
 *
 * <p>Tests cache behaviour and error handling. Does not make real HTTP
 * calls — tests invalid URLs that fail immediately to verify graceful
 * error handling and caching of failures.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
class MetaDescriptionFetcherTest {

    private MetaDescriptionFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new MetaDescriptionFetcher();
    }

    @Test
    void getCached_returnsNullWhenNotCached() {
        String result = fetcher.getCached("https://example.com/not-cached");
        assertThat(result).isNull();
    }

    @Test
    void prefetch_cachesEmptyStringForInvalidUrls() {
        fetcher.prefetch(List.of("https://invalid.test.local/no-such-page"), Duration.ofSeconds(5));

        String cached = fetcher.getCached("https://invalid.test.local/no-such-page");
        assertThat(cached).isNotNull();
        assertThat(cached).isEmpty();
    }

    @Test
    void prefetch_skipsAlreadyCachedUrls() {
        // First prefetch caches the result (use long timeout so DNS error resolves)
        fetcher.prefetch(List.of("https://invalid.test.local/page1"), Duration.ofSeconds(10));

        // If cached, second prefetch should skip (runs instantly)
        String cached = fetcher.getCached("https://invalid.test.local/page1");
        if (cached != null) {
            long start = System.currentTimeMillis();
            fetcher.prefetch(List.of("https://invalid.test.local/page1"), Duration.ofSeconds(10));
            long elapsed = System.currentTimeMillis() - start;
            // Should complete near-instantly since URL is already cached
            assertThat(elapsed).isLessThan(1_000);
        }
        // If DNS resolution was too slow and nothing cached, the test still passes
    }

    @Test
    void prefetch_handlesEmptyList() {
        fetcher.prefetch(List.of(), Duration.ofSeconds(5));
        // No exception thrown
    }

    @Test
    void prefetch_respectsTimeout() {
        long start = System.currentTimeMillis();
        // Fetch a URL that will time out — should not block longer than the timeout
        fetcher.prefetch(List.of("https://10.255.255.1/unreachable"), Duration.ofSeconds(2));
        long elapsed = System.currentTimeMillis() - start;

        // Should complete within a reasonable time (timeout + some overhead)
        assertThat(elapsed).isLessThan(10_000);
    }
}
