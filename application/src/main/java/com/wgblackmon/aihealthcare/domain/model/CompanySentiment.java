package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated sentiment analysis for a company, computed from its recent
 * news articles via LLM analysis.
 *
 * <p>The {@code sentimentScore} is a continuous value from −1.0 (very negative)
 * to +1.0 (very positive), computed from the per-article sentiment counts.
 * The {@code overallSentiment} label is derived from this score.
 *
 * @param companySlug       kebab-case company identifier
 * @param companyName       display name
 * @param overallSentiment  aggregate sentiment label
 * @param sentimentScore    continuous score from −1.0 to +1.0
 * @param totalArticles     number of articles analysed
 * @param positiveCount     articles classified as POSITIVE
 * @param negativeCount     articles classified as NEGATIVE
 * @param mixedCount        articles classified as MIXED
 * @param neutralCount      articles classified as NEUTRAL
 * @param riskSummary       LLM-generated 2-3 sentence risk assessment
 * @param articleSentiments per-article sentiment results (may be empty for persisted snapshots)
 * @param analyzedAt        when this analysis was performed
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public record CompanySentiment(
        String companySlug,
        String companyName,
        SentimentLabel overallSentiment,
        double sentimentScore,
        int totalArticles,
        int positiveCount,
        int negativeCount,
        int mixedCount,
        int neutralCount,
        String riskSummary,
        List<ArticleSentiment> articleSentiments,
        Instant analyzedAt
) {

    /**
     * Compact constructor — validates required fields and defensively copies list.
     */
    public CompanySentiment {
        if (companySlug == null || companySlug.isBlank()) {
            throw new IllegalArgumentException("companySlug must not be blank");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName must not be blank");
        }
        if (overallSentiment == null) {
            throw new IllegalArgumentException("overallSentiment must not be null");
        }
        if (analyzedAt == null) {
            throw new IllegalArgumentException("analyzedAt must not be null");
        }
        articleSentiments = articleSentiments == null ? List.of() : List.copyOf(articleSentiments);
    }
}
