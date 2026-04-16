package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable domain record representing a persisted newsletter run.
 *
 * <p>A {@code NewsletterRun} is the permanent historical record of a newsletter
 * that was generated (and eventually sent) for a given week.  It stores both the
 * HTML-formatted email content and the bare plain-text equivalent so that:
 * <ul>
 *   <li>The subscriber archive web page can render the HTML in a styled
 *       {@code <div>} without re-generating it.</li>
 *   <li>The plain-text version is available in a {@code <textarea>} for
 *       accessibility and copy-paste use.</li>
 * </ul>
 *
 * <p>The {@code runId} is the primary key stored in the {@code newsletter_runs}
 * table and corresponds to the {@code draftId} supplied to
 * {@link com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase#generate}.
 *
 * <p>Rendering from {@link NewsletterDraft} to HTML and plain text is performed
 * by {@code NewsletterRenderer} in the application layer immediately after
 * generation.  This record is then saved via
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort}.
 *
 * @param runId             Primary key — equals the {@code draftId} passed to {@code generate()}.
 * @param title             Newsletter edition title (e.g. "AI in Healthcare Weekly").
 * @param weekOf            Publication date of the edition.
 * @param htmlContent       Full HTML email body, inline-styled, ready for rendering.
 * @param plainTextContent  Plain-text version with no HTML tags; suitable for a textarea.
 * @param status            Lifecycle status: DRAFT → SENT → ARCHIVED.
 * @param generatedAt       Timestamp when this run was generated.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public record NewsletterRun(
        String runId,
        String title,
        LocalDate weekOf,
        String htmlContent,
        String plainTextContent,
        NewsletterRunStatus status,
        Instant generatedAt
) {
    /**
     * Compact canonical constructor — validates all required fields.
     */
    public NewsletterRun {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (weekOf == null) {
            throw new IllegalArgumentException("weekOf must not be null");
        }
        if (htmlContent == null || htmlContent.isBlank()) {
            throw new IllegalArgumentException("htmlContent must not be blank");
        }
        if (plainTextContent == null || plainTextContent.isBlank()) {
            throw new IllegalArgumentException("plainTextContent must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
    }
}
