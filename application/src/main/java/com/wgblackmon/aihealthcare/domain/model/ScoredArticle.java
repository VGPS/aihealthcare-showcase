package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing an article scored by an LLM
 * for technological significance within a specific trend keyword context.
 *
 * <p>Scores follow a 1-10 rubric:
 * <ul>
 *   <li><b>1-3:</b> Noise — passing mention, opinion, or generic overview</li>
 *   <li><b>4-6:</b> Relevant but routine — market reports, incremental updates</li>
 *   <li><b>7-8:</b> Significant development — product launch, clinical result, FDA action</li>
 *   <li><b>9-10:</b> Landmark — paradigm shift, first-of-kind, breakthrough outcome</li>
 * </ul>
 *
 * @param articleId   the unique identifier of the scored article
 * @param title       the article title
 * @param score       significance score (1-10)
 * @param rationale   one-sentence LLM explanation of why this score was assigned
 * @param keyword     the trend keyword this article was scored against
 * @param url         the article URL for linking (nullable)
 * @param sourceName  the publication name, e.g. "MedCity News" (nullable)
 * @param publishedAt the article publication date (nullable)
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-24
 * @updated 2026-07-26
 */
public record ScoredArticle(
        String articleId,
        String title,
        int score,
        String rationale,
        String keyword,
        String url,
        String sourceName,
        Instant publishedAt
) {

    /**
     * Compact constructor — validates required fields.
     */
    public ScoredArticle {
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (score < 1 || score > 10) {
            throw new IllegalArgumentException("score must be between 1 and 10");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
    }

    /**
     * Convenience constructor without metadata fields — backward compatible
     * with existing callers that don't have article metadata available.
     */
    public ScoredArticle(String articleId, String title, int score,
                          String rationale, String keyword) {
        this(articleId, title, score, rationale, keyword, null, null, null);
    }
}
