package com.wgblackmon.aihealthcare.domain.model;

/**
 * Per-article sentiment analysis result produced by the LLM.
 *
 * @param articleId  the scored article's unique identifier
 * @param title      the article title
 * @param sentiment  classified sentiment label
 * @param confidence confidence of the classification (0.0 to 1.0)
 * @param rationale  one-sentence explanation of the sentiment assignment
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public record ArticleSentiment(
        String articleId,
        String title,
        SentimentLabel sentiment,
        double confidence,
        String rationale
) {

    /**
     * Compact constructor — validates required fields.
     */
    public ArticleSentiment {
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (sentiment == null) {
            throw new IllegalArgumentException("sentiment must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
}
