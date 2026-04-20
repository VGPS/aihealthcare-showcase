package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Outbound port for storing and retrieving content hashes of monitored web pages.
 *
 * <p>Used by the web monitoring pipeline to detect when a competitor page's
 * content has changed since the last harvest.  Implementations persist a
 * SHA-256 hash keyed by URL so that repeated harvests of unchanged pages
 * are silently skipped.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
public interface ContentHashPort {

    /**
     * Retrieves the stored SHA-256 content hash for the given page URL.
     *
     * @param pageUrl the URL of the monitored page
     * @return the previously stored hash, or {@code null} if no hash exists
     */
    String getHash(String pageUrl);

    /**
     * Persists (upserts) the SHA-256 content hash for the given page URL.
     *
     * @param pageUrl     the URL of the monitored page
     * @param contentHash the SHA-256 hex digest of the page content
     */
    void saveHash(String pageUrl, String contentHash);
}
