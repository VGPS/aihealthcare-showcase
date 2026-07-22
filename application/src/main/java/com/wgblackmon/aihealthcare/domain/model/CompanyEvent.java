package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a detected event in a company's history.
 *
 * <p>Events are extracted from article titles and body text via keyword-based
 * heuristics in {@link com.wgblackmon.aihealthcare.domain.service.CompanyProfileService}.
 * Each event links back to a source article for provenance.
 *
 * @param eventId         unique identifier (UUID)
 * @param companySlug     slug of the parent {@link CompanyProfile}
 * @param eventType       classification of the event
 * @param title           event headline (typically the article title)
 * @param description     brief description of the event
 * @param sourceArticleId ID of the article that triggered this event detection
 * @param occurredAt      when the event happened (article publish date)
 * @param detectedAt      when the system detected this event
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public record CompanyEvent(
        String eventId,
        String companySlug,
        CompanyEventType eventType,
        String title,
        String description,
        String sourceArticleId,
        Instant occurredAt,
        Instant detectedAt
) {

    /**
     * Compact constructor — validates required fields.
     */
    public CompanyEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (companySlug == null || companySlug.isBlank()) {
            throw new IllegalArgumentException("companySlug must not be blank");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("detectedAt must not be null");
        }
    }
}
