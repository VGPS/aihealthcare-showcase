package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code company_sentiments} table.
 *
 * <p>Stores the latest sentiment analysis snapshot for a single company.
 * The companySlug is the natural key (upsert by slug). Per-article sentiment
 * details are stored as a JSON CLOB and deserialized by
 * {@link CompanySentimentAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Entity
@Table(name = "company_sentiments")
public class CompanySentimentEntity {

    @Id
    @Column(name = "company_slug", length = 200)
    private String companySlug;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "overall_sentiment", nullable = false, length = 20)
    private String overallSentiment;

    @Column(name = "sentiment_score")
    private double sentimentScore;

    @Column(name = "total_articles")
    private int totalArticles;

    @Column(name = "positive_count")
    private int positiveCount;

    @Column(name = "negative_count")
    private int negativeCount;

    @Column(name = "mixed_count")
    private int mixedCount;

    @Column(name = "neutral_count")
    private int neutralCount;

    @Column(name = "risk_summary", columnDefinition = "TEXT")
    private String riskSummary;

    @Column(name = "article_sentiments_json", columnDefinition = "TEXT")
    private String articleSentimentsJson;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    /** Required no-arg constructor for JPA. */
    public CompanySentimentEntity() {}

    public String getCompanySlug() { return companySlug; }
    public void setCompanySlug(String companySlug) { this.companySlug = companySlug; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getOverallSentiment() { return overallSentiment; }
    public void setOverallSentiment(String overallSentiment) { this.overallSentiment = overallSentiment; }

    public double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }

    public int getTotalArticles() { return totalArticles; }
    public void setTotalArticles(int totalArticles) { this.totalArticles = totalArticles; }

    public int getPositiveCount() { return positiveCount; }
    public void setPositiveCount(int positiveCount) { this.positiveCount = positiveCount; }

    public int getNegativeCount() { return negativeCount; }
    public void setNegativeCount(int negativeCount) { this.negativeCount = negativeCount; }

    public int getMixedCount() { return mixedCount; }
    public void setMixedCount(int mixedCount) { this.mixedCount = mixedCount; }

    public int getNeutralCount() { return neutralCount; }
    public void setNeutralCount(int neutralCount) { this.neutralCount = neutralCount; }

    public String getRiskSummary() { return riskSummary; }
    public void setRiskSummary(String riskSummary) { this.riskSummary = riskSummary; }

    public String getArticleSentimentsJson() { return articleSentimentsJson; }
    public void setArticleSentimentsJson(String articleSentimentsJson) { this.articleSentimentsJson = articleSentimentsJson; }

    public Instant getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Instant analyzedAt) { this.analyzedAt = analyzedAt; }
}
