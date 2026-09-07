package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.SourceCheckResult;

/**
 * Outbound port for checking whether a law source URL's content has changed.
 *
 * <p>Implementations fetch the URL, compute a SHA-256 hash of the response
 * body, and compare it to a previously stored hash. The result indicates
 * whether the content changed, along with the HTTP status and new hash.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
public interface LawSourceMonitorPort {

    /**
     * Fetches the given URL and compares its content hash to the stored hash.
     *
     * @param url          the source URL to check
     * @param storedHash   the previously stored SHA-256 hash (null if never checked)
     * @return the check result with new hash, HTTP status, and changed flag
     */
    SourceCheckResult checkUrl(String url, String storedHash);
}
