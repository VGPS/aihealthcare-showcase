package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * REST response record for a single private funding round.
 *
 * <p>Returned by {@code GET /api/market-digest/funding} endpoint.
 * Represents a private-company funding event with round details,
 * disclosed amount (nullable), and lead investor names.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
public record PrivateFundingRoundResponse(
        String companyName,
        String roundStage,
        Long amountUsd,
        List<String> leadInvestors,
        String announcedAt,
        String sourceUrl
) {}
