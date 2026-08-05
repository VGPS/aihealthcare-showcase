package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure domain service that detects business deal signals in recently
 * harvested articles using keyword matching and confidence scoring.
 *
 * <p>Scans articles from the last 7 days for funding, acquisition,
 * partnership, IPO, and product launch signals. Each detected signal
 * is persisted and available for webhook notification dispatch.
 *
 * <p>No LLM dependency — uses keyword matching for fast, deterministic
 * detection. Confidence is based on keyword density in the article text.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public class DealSignalDetectionService implements DetectDealSignalsUseCase {

    private static final int SCAN_DAYS = 7;

    private static final String[] FUNDING_KEYWORDS = {
            "raises", "raised", "funding", "series a", "series b", "series c", "series d",
            "seed round", "investment", "venture capital", "million", "billion",
            "fundraising", "capital raise", "growth equity", "led by"
    };

    private static final String[] ACQUISITION_KEYWORDS = {
            "acquires", "acquired", "acquisition", "merger", "merge", "takeover",
            "buyout", "bought", "purchase", "deal to acquire", "agreed to buy"
    };

    private static final String[] PARTNERSHIP_KEYWORDS = {
            "partners with", "partnership", "collaboration", "joint venture",
            "strategic alliance", "teamed up", "signs deal", "agreement with",
            "integrates with", "collaboration agreement"
    };

    private static final String[] IPO_KEYWORDS = {
            "ipo", "initial public offering", "goes public", "public offering",
            "stock exchange", "nasdaq", "nyse", "direct listing", "spac"
    };

    private static final String[] PRODUCT_LAUNCH_KEYWORDS = {
            "launches", "launched", "unveils", "introduces", "announces",
            "fda clearance", "fda approval", "ce mark", "510(k)",
            "new product", "now available", "general availability"
    };

    private final ArticleIngestionPort articleIngestionPort;
    private final DealSignalPort dealSignalPort;

    public DealSignalDetectionService(ArticleIngestionPort articleIngestionPort,
                                       DealSignalPort dealSignalPort) {
        this.articleIngestionPort = articleIngestionPort;
        this.dealSignalPort = dealSignalPort;
    }

    @Override
    public List<DealSignal> detectSignals() {
        List<NewsArticle> recentArticles = articleIngestionPort.fetchRecentArticles(SCAN_DAYS);
        List<DealSignal> newSignals = new ArrayList<>();
        Instant now = Instant.now();

        for (NewsArticle article : recentArticles) {
            if (dealSignalPort.existsByArticleId(article.articleId())) {
                continue;
            }

            String text = buildSearchText(article);
            DealSignalType bestType = null;
            double bestConfidence = 0.0;
            int bestMatchCount = 0;

            int fundingHits = countKeywordHits(text, FUNDING_KEYWORDS);
            if (fundingHits > 0 && fundingHits > bestMatchCount) {
                bestType = DealSignalType.FUNDING;
                bestMatchCount = fundingHits;
                bestConfidence = Math.min(1.0, 0.4 + (fundingHits * 0.15));
            }

            int acqHits = countKeywordHits(text, ACQUISITION_KEYWORDS);
            if (acqHits > 0 && acqHits > bestMatchCount) {
                bestType = DealSignalType.ACQUISITION;
                bestMatchCount = acqHits;
                bestConfidence = Math.min(1.0, 0.4 + (acqHits * 0.15));
            }

            int partnerHits = countKeywordHits(text, PARTNERSHIP_KEYWORDS);
            if (partnerHits > 0 && partnerHits > bestMatchCount) {
                bestType = DealSignalType.PARTNERSHIP;
                bestMatchCount = partnerHits;
                bestConfidence = Math.min(1.0, 0.4 + (partnerHits * 0.15));
            }

            int ipoHits = countKeywordHits(text, IPO_KEYWORDS);
            if (ipoHits > 0 && ipoHits > bestMatchCount) {
                bestType = DealSignalType.IPO;
                bestMatchCount = ipoHits;
                bestConfidence = Math.min(1.0, 0.4 + (ipoHits * 0.15));
            }

            int launchHits = countKeywordHits(text, PRODUCT_LAUNCH_KEYWORDS);
            if (launchHits > 0 && launchHits > bestMatchCount) {
                bestType = DealSignalType.PRODUCT_LAUNCH;
                bestMatchCount = launchHits;
                bestConfidence = Math.min(1.0, 0.4 + (launchHits * 0.15));
            }

            if (bestType != null && bestConfidence >= 0.5) {
                String summary = buildSummary(bestType, article.title());
                DealSignal signal = new DealSignal(
                        UUID.randomUUID().toString(),
                        article.articleId(),
                        article.title(),
                        bestType,
                        extractCompanyHint(article),
                        summary,
                        bestConfidence,
                        now
                );
                newSignals.add(signal);
            }
        }

        if (!newSignals.isEmpty()) {
            dealSignalPort.saveAll(newSignals);
        }

        return newSignals;
    }

    @Override
    public List<DealSignal> getRecentSignals(int limit) {
        return dealSignalPort.findRecent(limit);
    }

    private String buildSearchText(NewsArticle article) {
        StringBuilder sb = new StringBuilder();
        if (article.title() != null) {
            sb.append(article.title());
        }
        if (article.bodyText() != null && !article.bodyText().isBlank()) {
            sb.append(" ").append(article.bodyText());
        }
        return sb.toString().toLowerCase(Locale.ENGLISH);
    }

    private int countKeywordHits(String text, String[] keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    private String buildSummary(DealSignalType type, String title) {
        switch (type) {
            case FUNDING:
                return "Funding activity detected: " + title;
            case ACQUISITION:
                return "Acquisition/merger activity detected: " + title;
            case PARTNERSHIP:
                return "Partnership/collaboration announced: " + title;
            case IPO:
                return "IPO/public offering activity detected: " + title;
            case PRODUCT_LAUNCH:
                return "Product launch/regulatory clearance detected: " + title;
            default:
                return "Deal signal detected: " + title;
        }
    }

    private String extractCompanyHint(NewsArticle article) {
        if (article.sourceName() != null && !article.sourceName().isBlank()) {
            return article.sourceName();
        }
        if (article.topic() != null && !article.topic().isBlank()) {
            return article.topic();
        }
        return "Unknown";
    }
}
