package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable domain record representing a fully assembled newsletter draft
 * ready for review and delivery.
 *
 * <p>The draft is the top-level aggregate produced by the AI generation step. It contains:
 * <ul>
 *   <li>An AI-written {@code introduction} paragraph.</li>
 *   <li>An ordered list of {@link NewsletterSection}s, one per major topic/theme.</li>
 *   <li>The complete list of {@link NewsArticle}s that were used as source material —
 *       rendered as attribution links at the bottom of the published issue.</li>
 * </ul>
 * Both {@code sections} and {@code sourceArticles} are defensively copied and immutable
 * after construction.
 *
 * @param draftId        Unique identifier for this draft (e.g. "draft-2025-01-27-001").
 * @param runId          The ingestion run this draft was generated from.
 * @param title          Newsletter issue title.
 * @param weekOf         The Monday of the week this newsletter covers.
 * @param introduction   AI-generated introductory paragraph.
 * @param sections       Ordered newsletter sections produced by AI summarization.
 * @param sourceArticles All articles ingested for this run; used for bottom-of-issue attribution.
 * @param generatedAt    Timestamp of when this draft was generated.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-04-04
 */
public record NewsletterDraft(
        String draftId,
        String runId,
        String title,
        LocalDate weekOf,
        String introduction,
        List<NewsletterSection> sections,
        List<NewsArticle> sourceArticles,
        Instant generatedAt
) {
    public NewsletterDraft {
        if (draftId == null || draftId.isBlank()) {
            throw new IllegalArgumentException("draftId must not be blank");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (weekOf == null) {
            throw new IllegalArgumentException("weekOf must not be null");
        }
        if (introduction == null || introduction.isBlank()) {
            throw new IllegalArgumentException("introduction must not be blank");
        }
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("sections must contain at least one entry");
        }
        if (sourceArticles == null || sourceArticles.isEmpty()) {
            throw new IllegalArgumentException("sourceArticles must contain at least one entry");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
        // Defensive copies
        sections = List.copyOf(sections);
        sourceArticles = List.copyOf(sourceArticles);
    }
}
