package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;

/**
 * Response DTO for a stored page content hash entry.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
public record PageHashResponse(String pageUrl, String contentHash, Instant lastCheckedAt) {
}
