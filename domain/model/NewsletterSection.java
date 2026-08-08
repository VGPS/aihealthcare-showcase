package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing a single section within a {@link NewsletterDraft},
 * covering one topic or theme.
 *
 * <p>Sections are produced by the AI summarization step. The AI both generates the
 * written content (headline and summary) and classifies the section's editorial role
 * via {@link SectionType}. Each section also records the
 * {@link NewsArticle#articleId()} values of its source material so the newsletter
 * can render proper attribution links at the bottom of the issue.
 *
 * <p>The {@code articleIds} list is defensively copied and cannot be mutated after
 * construction. All fields except {@code articleIds} are validated as non-null/non-blank
 * in the compact constructor.
 *
 * @param sectionId   Unique identifier for this section (e.g. "section-001").
 * @param sectionType Editorial classification of this section's content.
 * @param topic       The search topic/keyword this section was derived from.
 * @param headline    AI-generated headline for this section.
 * @param summary     AI-generated summary paragraph (typically 2–4 sentences).
 * @param articleIds  IDs of the {@link NewsArticle}s used to produce this section.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-04-04
 */
public record NewsletterSection(
        String sectionId,
        SectionType sectionType,
        String topic,
        String headline,
        String summary,
        List<String> articleIds
) {
    /**
     * Compact canonical constructor — validates all required fields and defensively
     * copies the {@code articleIds} list.
     */
    public NewsletterSection {
        if (sectionId == null || sectionId.isBlank()) {
            throw new IllegalArgumentException("sectionId must not be blank");
        }
        if (sectionType == null) {
            throw new IllegalArgumentException("sectionType must not be null");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (headline == null || headline.isBlank()) {
            throw new IllegalArgumentException("headline must not be blank");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (articleIds == null || articleIds.isEmpty()) {
            throw new IllegalArgumentException("articleIds must contain at least one entry");
        }
        // Defensive copy — records are immutable by convention
        articleIds = List.copyOf(articleIds);
    }
}
