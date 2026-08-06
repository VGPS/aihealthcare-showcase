package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealClassificationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Domain service that detects business deal signals in recently harvested
 * articles using keyword matching with optional LLM refinement.
 *
 * <p>Scans articles from the last 7 days for funding, acquisition,
 * partnership, IPO, and product launch signals. When a
 * {@link DealClassificationPort} is available, keyword-matched candidates
 * are sent to the LLM for confirmation, enriched with deal amount,
 * counterparty, and analysis. Without the LLM port, falls back to
 * keyword-only detection.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-06
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
    private final DealClassificationPort classificationPort;
    private final DealEnrichmentService enrichmentService;

    public DealSignalDetectionService(ArticleIngestionPort articleIngestionPort,
                                       DealSignalPort dealSignalPort,
                                       DealClassificationPort classificationPort,
                                       DealEnrichmentService enrichmentService) {
        this.articleIngestionPort = articleIngestionPort;
        this.dealSignalPort = dealSignalPort;
        this.classificationPort = classificationPort;
        this.enrichmentService = enrichmentService;
    }

    @Override
    public List<DealSignal> detectSignals() {
        List<NewsArticle> recentArticles = articleIngestionPort.fetchRecentArticles(SCAN_DAYS);
        List<NewsArticle> candidates = new ArrayList<>();
        Instant now = Instant.now();

        for (NewsArticle article : recentArticles) {
            if (dealSignalPort.existsByArticleId(article.articleId())) {
                continue;
            }
            String text = buildSearchText(article);
            if (hasKeywordMatch(text)) {
                candidates.add(article);
            }
        }

        List<DealSignal> newSignals;
        if (classificationPort != null && !candidates.isEmpty()) {
            newSignals = classificationPort.classifyDeals(candidates);
        } else {
            newSignals = keywordClassify(candidates, now);
        }

        List<DealSignal> deduped = deduplicateByCompanyTypeAndDay(newSignals);

        if (!deduped.isEmpty()) {
            dealSignalPort.saveAll(deduped);
        }

        return deduped;
    }

    @Override
    public List<DealSignal> getRecentSignals(int limit) {
        return dealSignalPort.findRecent(limit);
    }

    @Override
    public DealSignal getSignalById(String signalId) {
        return dealSignalPort.findById(signalId);
    }

    @Override
    public List<DealSignal> getSignalsByType(DealSignalType type, int limit) {
        return dealSignalPort.findByType(type.name(), limit);
    }

    @Override
    public DealContext getSignalWithContext(String signalId) {
        DealSignal signal = dealSignalPort.findById(signalId);
        if (signal == null) {
            return null;
        }
        if (enrichmentService == null) {
            return new DealContext(signal, null, null, List.of(), null);
        }
        return enrichmentService.enrich(signal);
    }

    private boolean hasKeywordMatch(String text) {
        return countKeywordHits(text, FUNDING_KEYWORDS) > 0
                || countKeywordHits(text, ACQUISITION_KEYWORDS) > 0
                || countKeywordHits(text, PARTNERSHIP_KEYWORDS) > 0
                || countKeywordHits(text, IPO_KEYWORDS) > 0
                || countKeywordHits(text, PRODUCT_LAUNCH_KEYWORDS) > 0;
    }

    private List<DealSignal> keywordClassify(List<NewsArticle> candidates, Instant now) {
        List<DealSignal> signals = new ArrayList<>();
        for (NewsArticle article : candidates) {
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
                String sourceUrl = article.url() != null ? article.url().toString() : null;
                DealSignal signal = new DealSignal(
                        UUID.randomUUID().toString(),
                        article.articleId(),
                        article.title(),
                        bestType,
                        extractCompanyHint(article),
                        summary,
                        bestConfidence,
                        now,
                        null,
                        null,
                        sourceUrl,
                        null
                );
                signals.add(signal);
            }
        }
        return signals;
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

    private List<DealSignal> deduplicateByCompanyTypeAndDay(List<DealSignal> signals) {
        Map<String, DealSignal> bestByKey = new LinkedHashMap<>();
        for (DealSignal signal : signals) {
            String company = signal.companyName() != null
                    ? signal.companyName().toLowerCase(Locale.ENGLISH).trim() : "";
            String dayStr = signal.detectedAt().toString().substring(0, 10);
            String key = company + "|" + signal.signalType().name() + "|" + dayStr;

            DealSignal existing = bestByKey.get(key);
            if (existing == null || signal.confidence() > existing.confidence()) {
                bestByKey.put(key, signal);
            }
        }
        return new ArrayList<>(bestByKey.values());
    }
}
