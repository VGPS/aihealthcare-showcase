package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Lightweight summary record for listing market digests without loading full entry details.
 *
 * <p>Returned by {@code GET /api/market-digest?from=&to=}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record MarketDigestSummary(
        LocalDate date,
        Instant generatedAt,
        int entryCount
) {}
