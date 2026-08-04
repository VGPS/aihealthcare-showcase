package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ArticleSentiment;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SentimentAnalysisPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Domain service orchestrating company-level sentiment analysis.
 *
 * <p>Loads all tracked {@link CompanyProfile} records, fetches their linked
 * articles, delegates per-article sentiment classification to the LLM via
 * {@link SentimentAnalysisPort}, aggregates the results into a
 * {@link CompanySentiment} record, and persists via {@link CompanySentimentPort}.
 *
 * <p>Has no Spring dependencies — wired via {@code AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public class CompanySentimentService implements AnalyzeCompanySentimentUseCase {

    private static final int MIN_ARTICLES = 2;

    private final CompanyProfilePort companyProfilePort;
    private final ArticleIngestionPort articleIngestionPort;
    private final SentimentAnalysisPort sentimentAnalysisPort;
    private final CompanySentimentPort companySentimentPort;

    public CompanySentimentService(CompanyProfilePort companyProfilePort,
                                   ArticleIngestionPort articleIngestionPort,
                                   SentimentAnalysisPort sentimentAnalysisPort,
                                   CompanySentimentPort companySentimentPort) {
        this.companyProfilePort = companyProfilePort;
        this.articleIngestionPort = articleIngestionPort;
        this.sentimentAnalysisPort = sentimentAnalysisPort;
        this.companySentimentPort = companySentimentPort;
    }

    @Override
    public List<CompanySentiment> analyzeAll() {
        List<CompanyProfile> profiles = companyProfilePort.findAll();
        List<CompanySentiment> results = new ArrayList<>();

        for (CompanyProfile profile : profiles) {
            if (profile.articleIds().isEmpty()) {
                continue;
            }

            List<NewsArticle> articles = articleIngestionPort.fetchArticlesByIds(profile.articleIds());
            if (articles.size() < MIN_ARTICLES) {
                continue;
            }

            List<ArticleSentiment> sentiments = sentimentAnalysisPort.analyzeSentiment(articles, profile.name());
            if (sentiments.isEmpty()) {
                continue;
            }

            CompanySentiment companySentiment = aggregate(profile.slug(), profile.name(), sentiments);
            companySentimentPort.save(companySentiment);
            results.add(companySentiment);
        }

        return results;
    }

    @Override
    public Optional<CompanySentiment> getBySlug(String companySlug) {
        return companySentimentPort.findBySlug(companySlug);
    }

    @Override
    public List<CompanySentiment> getAll() {
        return companySentimentPort.findAll();
    }

    /**
     * Aggregates per-article sentiments into a company-level summary.
     */
    CompanySentiment aggregate(String slug, String name, List<ArticleSentiment> sentiments) {
        int positiveCount = 0;
        int negativeCount = 0;
        int mixedCount = 0;
        int neutralCount = 0;

        for (ArticleSentiment s : sentiments) {
            switch (s.sentiment()) {
                case POSITIVE:
                    positiveCount++;
                    break;
                case NEGATIVE:
                    negativeCount++;
                    break;
                case MIXED:
                    mixedCount++;
                    break;
                case NEUTRAL:
                    neutralCount++;
                    break;
            }
        }

        int total = sentiments.size();
        double score = computeScore(positiveCount, negativeCount, total);
        SentimentLabel overall = deriveLabel(score);
        String riskSummary = buildRiskSummary(name, positiveCount, negativeCount, mixedCount, neutralCount, total);

        return new CompanySentiment(
                slug,
                name,
                overall,
                score,
                total,
                positiveCount,
                negativeCount,
                mixedCount,
                neutralCount,
                riskSummary,
                sentiments,
                Instant.now()
        );
    }

    /**
     * Computes a continuous sentiment score from −1.0 (all negative) to +1.0 (all positive).
     */
    private double computeScore(int positiveCount, int negativeCount, int total) {
        if (total == 0) {
            return 0.0;
        }
        return (double) (positiveCount - negativeCount) / total;
    }

    /**
     * Maps a continuous score to a discrete label.
     */
    private SentimentLabel deriveLabel(double score) {
        if (score > 0.25) {
            return SentimentLabel.POSITIVE;
        }
        if (score < -0.25) {
            return SentimentLabel.NEGATIVE;
        }
        if (score > -0.10 && score < 0.10) {
            return SentimentLabel.NEUTRAL;
        }
        return SentimentLabel.MIXED;
    }

    private String buildRiskSummary(String name, int pos, int neg, int mixed, int neutral, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" has ").append(total).append(" recent articles analyzed. ");

        if (neg == 0 && pos > 0) {
            sb.append("Coverage is predominantly positive with no negative signals. Low risk profile.");
        } else if (neg > pos) {
            sb.append("Negative coverage (").append(neg).append(") outweighs positive (").append(pos)
              .append("). Elevated risk — monitor for developing issues.");
        } else if (neg > 0 && neg <= pos) {
            sb.append("Mixed signals with ").append(neg).append(" negative and ").append(pos)
              .append(" positive articles. Moderate risk — watch for trend changes.");
        } else {
            sb.append("Coverage is primarily neutral or factual. No significant risk signals detected.");
        }

        return sb.toString();
    }
}
