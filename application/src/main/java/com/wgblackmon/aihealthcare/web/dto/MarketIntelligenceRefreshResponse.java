package com.wgblackmon.aihealthcare.web.dto;

/**
 * Response DTO returned by {@code POST /api/v1/market-intelligence/refresh}.
 *
 * <p>Carries the metadata of the freshly generated market intelligence report:
 * the path of the written HTML file, the report date, and the length of the
 * generated HTML content.
 *
 * @param filePath    Absolute path of the written HTML file.
 * @param reportDate  ISO-8601 date string ({@code yyyy-MM-dd}) of the report.
 * @param htmlLength  Number of characters in the generated HTML document.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
public record MarketIntelligenceRefreshResponse(
        String filePath,
        String reportDate,
        int htmlLength
) {}
