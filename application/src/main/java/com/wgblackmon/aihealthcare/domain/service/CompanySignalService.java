package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanySignal;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure domain service that computes {@link CompanySignal} scores for a list of
 * AI healthcare companies by cross-referencing three data sources in a single pass:
 *
 * <ol>
 *   <li>Article frequency — articles mentioning the company name in the last 90 days</li>
 *   <li>Deal signals — FUNDING signal presence (type, amount, date)</li>
 *   <li>Sentiment snapshots — overallSentiment and score for watch-list flagging</li>
 * </ol>
 *
 * <p>The service makes exactly three port calls per invocation (one per data source)
 * and resolves matches in memory, avoiding N+1 database queries.  All port calls
 * are best-effort — empty results from any source produce zero-valued signal fields
 * rather than errors.
 *
 * <p>This class has no Spring or Lombok dependencies. It is wired via {@code AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-27
 * @updated 2026-08-27
 */
public class CompanySignalService {

    private static final int ARTICLE_VELOCITY_CAP = 30;
    private static final int ARTICLE_VELOCITY_PTS = 3;
    private static final int DEAL_BONUS = 20;
    private static final int SENTIMENT_PENALTY = -15;
    private static final double WATCH_THRESHOLD = -0.2;
    private static final int ARTICLE_LOOKBACK_DAYS = 90;
    private static final int MAX_FUNDING_SIGNALS = 500;

    private final ArticleIngestionPort articlePort;
    private final DealSignalPort dealPort;
    private final CompanySentimentPort sentimentPort;

    public CompanySignalService(ArticleIngestionPort articlePort,
                                 DealSignalPort dealPort,
                                 CompanySentimentPort sentimentPort) {
        this.articlePort = articlePort;
        this.dealPort = dealPort;
        this.sentimentPort = sentimentPort;
    }

    /**
     * Builds a signal map for every company in the supplied list, keyed by
     * {@link HealthcareAiCompany#companyId()}.
     *
     * @param companies companies to score; must not be null
     * @return map from companyId to its computed signal (never null, may be empty)
     */
    public Map<String, CompanySignal> buildSignalMap(List<HealthcareAiCompany> companies) {
        List<NewsArticle> recentArticles = articlePort.fetchRecentArticles(ARTICLE_LOOKBACK_DAYS);
        List<DealSignal> fundingDeals = dealPort.findByType("FUNDING", MAX_FUNDING_SIGNALS, 0);

        List<CompanySentiment> allSentiments = sentimentPort.findAll();
        Map<String, CompanySentiment> sentimentBySlug = new HashMap<>();
        for (CompanySentiment s : allSentiments) {
            sentimentBySlug.put(s.companySlug(), s);
        }

        Map<String, CompanySignal> result = new HashMap<>();
        for (HealthcareAiCompany company : companies) {
            result.put(company.companyId(),
                    buildSignal(company, recentArticles, fundingDeals, sentimentBySlug));
        }
        return result;
    }

    private CompanySignal buildSignal(HealthcareAiCompany company,
                                       List<NewsArticle> recentArticles,
                                       List<DealSignal> fundingDeals,
                                       Map<String, CompanySentiment> sentimentBySlug) {
        String nameLower = company.name().toLowerCase();
        String nameKey = firstWord(nameLower);

        // Count articles whose title mentions this company by name
        int articleCount = 0;
        for (NewsArticle article : recentArticles) {
            String titleLower = article.title().toLowerCase();
            if (titleLower.contains(nameLower) || (nameKey.length() > 4 && titleLower.contains(nameKey))) {
                articleCount++;
            }
        }

        // Find the most recent funding deal matching this company
        DealSignal latestDeal = null;
        for (DealSignal deal : fundingDeals) {
            if (deal.companyName() == null) continue;
            String dealName = deal.companyName().toLowerCase();
            boolean matches = dealName.contains(nameKey) || nameLower.contains(firstWord(dealName));
            if (matches) {
                if (latestDeal == null || deal.detectedAt().isAfter(latestDeal.detectedAt())) {
                    latestDeal = deal;
                }
            }
        }

        // Look up sentiment by slug
        String slug = toSlug(company.name());
        CompanySentiment sentiment = sentimentBySlug.get(slug);

        // Composite score
        int velScore = Math.min(articleCount * ARTICLE_VELOCITY_PTS, ARTICLE_VELOCITY_CAP);
        int dealScore = (latestDeal != null) ? DEAL_BONUS : 0;
        int sentPenalty = (sentiment != null && sentiment.sentimentScore() < WATCH_THRESHOLD)
                ? SENTIMENT_PENALTY : 0;
        int relevanceScore = Math.max(0, velScore + dealScore + sentPenalty);

        return new CompanySignal(
                company.companyId(),
                articleCount,
                latestDeal != null ? latestDeal.signalType().name() : null,
                latestDeal != null ? latestDeal.dealAmount() : null,
                latestDeal != null ? latestDeal.detectedAt() : null,
                sentiment != null ? sentiment.sentimentScore() : 0.0,
                sentiment != null ? sentiment.overallSentiment().name() : null,
                sentiment != null,
                relevanceScore
        );
    }

    /** Returns the first word longer than 3 characters, or the raw first token. */
    private static String firstWord(String s) {
        if (s == null || s.isBlank()) return "";
        String[] parts = s.trim().split("\\s+");
        for (String p : parts) {
            if (p.length() > 3) return p;
        }
        return parts[0];
    }

    /** Converts a display name to a URL-safe slug matching the slug stored in the DB. */
    static String toSlug(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
