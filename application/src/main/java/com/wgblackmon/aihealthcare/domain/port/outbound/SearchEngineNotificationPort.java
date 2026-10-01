package com.wgblackmon.aihealthcare.domain.port.outbound;

import java.util.List;

/**
 * Outbound port for proactively notifying search engines that content changed,
 * rather than waiting for the next scheduled sitemap crawl.
 *
 * <p>Implementations are expected to be best-effort and non-blocking to the
 * caller: notification failures must never affect the outcome of the
 * create/update operation that triggered them.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-30
 * @updated 2026-09-30
 */
public interface SearchEngineNotificationPort {

    /**
     * Notifies that the given absolute URLs were created or updated.
     *
     * @param absoluteUrls fully-qualified URLs (e.g. {@code https://app.bigskylabs.ai/wiki/foo})
     */
    void notifyUrlsChanged(List<String> absoluteUrls);
}
