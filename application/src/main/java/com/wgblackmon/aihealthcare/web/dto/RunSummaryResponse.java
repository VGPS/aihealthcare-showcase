package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Summary response DTO for a persisted newsletter run.
 *
 * <p>Returned as elements of the list by {@code GET /api/v1/runs}.
 * Does NOT include {@code htmlContent} or {@code plainTextContent} to keep
 * the list response compact — use {@code GET /api/v1/runs/{runId}} for the
 * full detail including both rendered formats.
 *
 * @param runId       Primary key of the run.
 * @param title       Newsletter edition title.
 * @param weekOf      Publication date of the edition.
 * @param status      Lifecycle status: "DRAFT", "SENT", or "ARCHIVED".
 * @param generatedAt Timestamp when the run was generated.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public record RunSummaryResponse(
        String runId,
        String title,
        LocalDate weekOf,
        String status,
        Instant generatedAt
) {}
