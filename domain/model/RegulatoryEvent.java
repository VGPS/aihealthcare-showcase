package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable domain record representing a regulatory event relevant to
 * AI in healthcare.
 *
 * <p>Covers FDA 510(k) clearances, De Novo classifications, CMS rules,
 * and other regulatory actions. Each event carries structured metadata
 * (reference numbers, applicant names, device names) that plain
 * {@link NewsArticle} records cannot express. The optional
 * {@code linkedArticleId} bridges to an originating article when one exists.
 *
 * @param eventId           unique identifier (UUID)
 * @param eventType         classification of the regulatory action
 * @param regulatoryBody    issuing federal agency (FDA, CMS, ONC)
 * @param title             human-readable event title
 * @param summary           short description or AI-generated summary (nullable)
 * @param referenceNumber   e.g. "K241234" for 510(k), "DEN210040" for De Novo (nullable)
 * @param applicantName     company/entity that filed (nullable)
 * @param deviceName        device or product name (nullable)
 * @param sourceUrl         original source URL
 * @param linkedArticleId   FK to {@link NewsArticle#articleId()} if one exists (nullable)
 * @param publishedAt       when the regulatory event was published/effective (nullable)
 * @param discoveredAt      when our system first discovered this event
 * @param aiHealthcareKeywords    extracted keywords relevant to AI/healthcare
 * @param outcomeStatus           lifecycle status (PENDING, CLEARED, APPROVED, etc.) — nullable
 * @param outcomeUpdatedAt        when the outcome was last checked or changed — nullable
 * @param clearanceType           510(k) clearance pathway (Traditional, Special, Abbreviated) — nullable
 * @param predicateDeviceNumber   predicate device reference for 510(k) (e.g. "K192345") — nullable
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-30
 */
public record RegulatoryEvent(
        String eventId,
        RegulatoryEventType eventType,
        RegulatoryBody regulatoryBody,
        String title,
        String summary,
        String referenceNumber,
        String applicantName,
        String deviceName,
        String sourceUrl,
        String linkedArticleId,
        Instant publishedAt,
        Instant discoveredAt,
        List<String> aiHealthcareKeywords,
        RegulatoryOutcomeStatus outcomeStatus,
        Instant outcomeUpdatedAt,
        String clearanceType,
        String predicateDeviceNumber
) {

    /** Compact constructor — validates required fields and defensive-copies the keyword list. */
    public RegulatoryEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (regulatoryBody == null) {
            throw new IllegalArgumentException("regulatoryBody must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (discoveredAt == null) {
            throw new IllegalArgumentException("discoveredAt must not be null");
        }
        aiHealthcareKeywords = aiHealthcareKeywords == null
                ? List.of()
                : List.copyOf(aiHealthcareKeywords);
    }
}
