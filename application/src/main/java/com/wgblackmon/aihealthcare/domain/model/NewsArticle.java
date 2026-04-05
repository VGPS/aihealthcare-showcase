package com.wgblackmon.aihealthcare.domain.model;

import java.net.URI;
import java.time.LocalDate;

/**
 * Immutable domain record representing a single article discovered
 * during a newsletter ingestion run.
 *
 * <p>Holds the article's title, source URL, extracted body text, the search
 * topic that led to its discovery, an optional author byline, and an optional
 * publication date. This record is the primary input to
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort}.
 * Both {@code author} and {@code publishedDate} are optional — not all sources
 * expose a reliable byline or publication date.
 *
 * @param articleId     Stable identifier assigned at ingestion time (e.g. "article-001").
 * @param title         Title of the article as scraped from the source page.
 * @param url           Canonical URL of the source article; included in newsletter attribution.
 * @param bodyText      Full or partial body text extracted for AI summarization.
 * @param topic         The search topic/keyword that led to this article's discovery.
 * @param author        Byline of the article's author, or {@code null} if not determinable.
 * @param publishedDate Publication date of the article, or {@code null} if not determinable.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-04-04
 */
public record NewsArticle(
        String articleId,
        String title,
        URI url,
        String bodyText,
        String topic,
        String author,
        LocalDate publishedDate
) {
    /**
     * Compact canonical constructor — validates required fields.
     */
    public NewsArticle {
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (url == null) {
            throw new IllegalArgumentException("url must not be null");
        }
        if (bodyText == null || bodyText.isBlank()) {
            throw new IllegalArgumentException("bodyText must not be blank");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
    }
}
