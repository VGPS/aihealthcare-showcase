package com.wgblackmon.aihealthcare.web.dto;

/**
 * REST response record for a single regulatory tracker entry.
 *
 * <p>Returned by {@code GET /api/market-digest/regulatory-tracker} endpoints.
 * Represents the current state of a regulatory rulemaking or guidance action,
 * including its lifecycle stage and any approaching comment deadline.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
public record RegulatoryTrackerResponse(
        String jurisdiction,
        String stage,
        String docketId,
        String title,
        String commentDeadline,
        String lastUpdatedAt
) {}
