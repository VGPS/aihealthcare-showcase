package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Fetches and caches HTML meta descriptions from article URLs.
 *
 * <p>Uses Jsoup to follow the URL (including Google News redirects),
 * extracts the {@code <meta name="description">} or {@code <meta property="og:description">}
 * tag content, and caches results in a {@link ConcurrentHashMap} for the
 * lifetime of the application.
 *
 * <p>Provides a {@link #prefetch(List, Duration)} method for parallel bulk
 * fetching with a bounded total wait time, so page rendering does not
 * block indefinitely.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
@Slf4j
@Component
public class MetaDescriptionFetcher {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AIHealthcareBot/1.0)";

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Returns the cached meta description for a URL, or null if not cached.
     *
     * @param url the article URL
     * @return cached description or null
     */
    public String getCached(String url) {
        log.debug("getCached() | url={}", url);
        String result = cache.get(url);
        log.debug("getCached() | return={}", result != null ? result.substring(0, Math.min(60, result.length())) + "..." : "null");
        return result;
    }

    /**
     * Fetches meta descriptions for a list of URLs in parallel, blocking
     * for at most {@code maxWait}. Results are stored in the internal cache.
     *
     * @param urls    the URLs to fetch descriptions for
     * @param maxWait maximum total time to wait for all fetches
     */
    public void prefetch(List<String> urls, Duration maxWait) {
        log.debug("prefetch() | urlCount={}, maxWait={}", urls.size(), maxWait);

        List<String> uncached = new ArrayList<>();
        for (String url : urls) {
            if (!cache.containsKey(url)) {
                uncached.add(url);
            }
        }

        if (uncached.isEmpty()) {
            log.debug("prefetch() | all URLs cached, return=void");
            return;
        }

        log.debug("prefetch() | fetching {} uncached URLs", uncached.size());
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(uncached.size(), 10));

        List<Future<?>> futures = new ArrayList<>();
        for (String url : uncached) {
            futures.add(executor.submit(() -> fetchAndCache(url)));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(maxWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("prefetch() | interrupted while waiting for meta description fetches");
        }

        log.debug("prefetch() | return=void, cached={}", cache.size());
    }

    private void fetchAndCache(String url) {
        log.debug("fetchAndCache() | url={}", url);

        // Google News RSS URLs use JavaScript redirects — Jsoup cannot follow them
        if (url.contains("news.google.com/rss/articles/")) {
            cache.put(url, "");
            log.debug("fetchAndCache() | skipping Google News redirect URL");
            return;
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .maxBodySize(500_000)
                    .followRedirects(true)
                    .get();

            String description = extractDescription(doc);
            if (description != null && !description.isBlank()) {
                cache.put(url, description);
                log.debug("fetchAndCache() | cached description for url={}, length={}", url, description.length());
            } else {
                // Cache empty string to avoid re-fetching
                cache.put(url, "");
                log.debug("fetchAndCache() | no description found for url={}", url);
            }
        } catch (Exception e) {
            // Cache empty string to avoid re-fetching on error
            cache.put(url, "");
            log.debug("fetchAndCache() | error fetching url={}: {}", url, e.getMessage());
        }
    }

    private String extractDescription(Document doc) {
        log.debug("extractDescription() | docTitle={}", doc.title());

        // Try og:description first (usually better quality)
        Element ogDesc = doc.selectFirst("meta[property=og:description]");
        if (ogDesc != null && !ogDesc.attr("content").isBlank()) {
            String result = ogDesc.attr("content").trim();
            log.debug("extractDescription() | return=og:description[{}]", result.length());
            return result;
        }

        // Fall back to standard meta description
        Element metaDesc = doc.selectFirst("meta[name=description]");
        if (metaDesc != null && !metaDesc.attr("content").isBlank()) {
            String result = metaDesc.attr("content").trim();
            log.debug("extractDescription() | return=meta:description[{}]", result.length());
            return result;
        }

        log.debug("extractDescription() | return=null");
        return null;
    }
}
