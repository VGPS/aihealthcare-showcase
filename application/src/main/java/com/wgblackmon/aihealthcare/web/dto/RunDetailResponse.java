package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Full detail response DTO for a single persisted newsletter run.
 *
 * <p>Returned by {@code GET /api/v1/runs/{runId}}.  Includes both
 * {@code htmlContent} (for rendering in a styled {@code <div>} on the
 * subscriber archive page) and {@code plainTextContent} (for display in a
 * {@code <textarea>} or accessibility use).
 *
 * @param runId            Primary key of the run.
 * @param title            Newsletter edition title.
 * @param weekOf           Publication date of the edition.
 * @param htmlContent      Full inline-styled HTML email body.
 * @param plainTextContent Plain text version with no HTML markup.
 * @param status           Lifecycle status: "DRAFT", "SENT", or "ARCHIVED".
 * @param generatedAt      Timestamp when the run was generated.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public record RunDetailResponse(
        String runId,
        String title,
        LocalDate weekOf,
        String htmlContent,
        String plainTextContent,
        String status,
        Instant generatedAt
) {}
