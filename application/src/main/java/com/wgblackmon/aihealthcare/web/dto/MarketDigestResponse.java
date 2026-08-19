package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * REST response record for a full market digest — date, generated timestamp, and
 * all qualifying entries.
 *
 * <p>Returned by {@code GET /api/market-digest/{date}} and
 * {@code GET /api/market-digest/latest}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record MarketDigestResponse(
        LocalDate date,
        Instant generatedAt,
        int entryCount,
        List<MarketDigestEntryResponse> entries
) {}
