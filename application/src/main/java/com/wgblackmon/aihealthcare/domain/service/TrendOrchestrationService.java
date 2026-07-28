package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSummaryPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application-layer service implementing the trend detection use case.
 *
 * <p>Orchestrates article retrieval, trend analysis, and snapshot persistence.
 * The actual frequency analysis is delegated to {@link TrendDetectionService}.
 *
 * <p>This class carries no Spring annotations — it is wired as a {@code @Bean}
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-22
 * @updated 2026-07-28
 */
@Slf4j
public class TrendOrchestrationService implements DetectTrendsUseCase {

    private static final int LOOKBACK_DAYS = 180;
    private static final int MAX_ARTICLES_PER_KEYWORD = 5;
    private static final int DEFAULT_MAX_SUMMARIES = 5;

    private final ArticleIngestionPort articleIngestionPort;
    private final TrendDetectionService trendDetectionService;
    private final TrendSnapshotPort trendSnapshotPort;
    private final ArticleScoringPort articleScoringPort;
    private final TrendSummaryPort trendSummaryPort;
    private final boolean scoringEnabled;
    private final int scoreThreshold;
    private final int maxSummariesPerRun;

    public TrendOrchestrationService(ArticleIngestionPort articleIngestionPort,
                                     TrendDetectionService trendDetectionService,
                                     TrendSnapshotPort trendSnapshotPort,
                                     ArticleScoringPort articleScoringPort,
                                     TrendSummaryPort trendSummaryPort,
                                     boolean scoringEnabled,
                                     int scoreThreshold,
                                     int maxSummariesPerRun) {
        log.debug("TrendOrchestrationService() | articleIngestionPort={}, trendDetectionService={}, " +
                  "trendSnapshotPort={}, articleScoringPort={}, trendSummaryPort={}, " +
                  "scoringEnabled={}, scoreThreshold={}, maxSummariesPerRun={}",
                  articleIngestionPort, trendDetectionService, trendSnapshotPort,
                  articleScoringPort, trendSummaryPort, scoringEnabled, scoreThreshold,
                  maxSummariesPerRun);
        this.articleIngestionPort = articleIngestionPort;
        this.trendDetectionService = trendDetectionService;
        this.trendSnapshotPort = trendSnapshotPort;
        this.articleScoringPort = articleScoringPort;
        this.trendSummaryPort = trendSummaryPort;
        this.scoringEnabled = scoringEnabled;
        this.scoreThreshold = scoreThreshold;
        this.maxSummariesPerRun = maxSummariesPerRun > 0 ? maxSummariesPerRun : DEFAULT_MAX_SUMMARIES;
    }

    @Override
    public TrendSnapshot detectTrends() {
        log.debug("detectTrends()");

        List<NewsArticle> articles = articleIngestionPort.fetchRecentArticles(LOOKBACK_DAYS);
        log.info("detectTrends() | loaded {} articles from last {} days", articles.size(), LOOKBACK_DAYS);

        // First pass: keyword frequency analysis
        TrendSnapshot baseSnapshot = trendDetectionService.detectTrends(articles, Instant.now());

        // Second pass: LLM scoring for rising keywords (if enabled)
        TrendSnapshot snapshot;
        if (scoringEnabled && articleScoringPort != null && !baseSnapshot.risingTopics().isEmpty()) {
            Map<String, List<ScoredArticle>> scoredByKeyword = scoreRisingKeywords(
                    baseSnapshot.risingTopics(), articles);
            snapshot = trendDetectionService.detectTrends(articles, Instant.now(), scoredByKeyword);
        } else {
            snapshot = baseSnapshot;
        }

        // Always attach matching articles to rising signals that have none
        snapshot = attachMatchingArticles(snapshot, articles);

        // Generate deep research summaries for top rising keywords
        if (trendSummaryPort != null && trendSummaryPort.isAvailable()
                && !snapshot.risingTopics().isEmpty()) {
            snapshot = generateTrendSummaries(snapshot, articles);
        }

        trendSnapshotPort.save(snapshot);

        log.info("detectTrends() | snapshot saved: rising={}, total={}",
                 snapshot.risingTopics().size(), snapshot.totalKeywords());
        log.debug("detectTrends() | return={}", snapshot);
        return snapshot;
    }

    /**
     * Scores articles for each rising keyword by calling the LLM scoring port.
     */
    private Map<String, List<ScoredArticle>> scoreRisingKeywords(
            List<TrendSignal> risingTopics, List<NewsArticle> allArticles) {
        log.debug("scoreRisingKeywords() | risingCount={}, totalArticles={}",
                  risingTopics.size(), allArticles.size());

        Map<String, List<ScoredArticle>> result = new HashMap<>();

        for (TrendSignal signal : risingTopics) {
            String keyword = signal.keyword();

            // Find articles containing this keyword in title or body
            List<NewsArticle> relevantArticles = new ArrayList<>();
            for (NewsArticle article : allArticles) {
                if (articleContainsKeyword(article, keyword)) {
                    relevantArticles.add(article);
                }
            }

            if (relevantArticles.isEmpty()) {
                continue;
            }

            try {
                List<ScoredArticle> scored = articleScoringPort.scoreArticles(
                        relevantArticles, keyword,
                        "Healthcare AI trend keyword: " + keyword,
                        scoreThreshold);

                // Limit to top N articles per keyword
                if (scored.size() > MAX_ARTICLES_PER_KEYWORD) {
                    scored = new ArrayList<>(scored.subList(0, MAX_ARTICLES_PER_KEYWORD));
                }

                if (!scored.isEmpty()) {
                    result.put(keyword, scored);
                }
            } catch (Exception e) {
                log.warn("scoreRisingKeywords() | scoring failed for keyword={}: {}",
                         keyword, e.getMessage());
            }
        }

        log.debug("scoreRisingKeywords() | return={} keywords with scored articles", result.size());
        return result;
    }

    /**
     * Generates deep-research trend summaries for the top N rising keywords
     * (capped by {@code maxSummariesPerRun} for cost control).
     */
    private TrendSnapshot generateTrendSummaries(TrendSnapshot snapshot, List<NewsArticle> articles) {
        log.debug("generateTrendSummaries() | risingCount={}, maxSummaries={}",
                  snapshot.risingTopics().size(), maxSummariesPerRun);

        boolean anySummary = false;
        int summariesGenerated = 0;
        List<TrendSignal> enriched = new ArrayList<>();

        for (TrendSignal signal : snapshot.risingTopics()) {
            // Cost guard: stop after maxSummariesPerRun
            if (summariesGenerated >= maxSummariesPerRun) {
                enriched.add(signal);
                continue;
            }

            // Find articles matching this keyword for context
            List<NewsArticle> relevant = new ArrayList<>();
            for (NewsArticle article : articles) {
                if (articleContainsKeyword(article, signal.keyword())) {
                    relevant.add(article);
                    if (relevant.size() >= 10) {
                        break;
                    }
                }
            }

            if (relevant.isEmpty()) {
                enriched.add(signal);
                continue;
            }

            try {
                String summary = trendSummaryPort.generateSummary(signal.keyword(), relevant);
                if (summary != null && !summary.isBlank()) {
                    anySummary = true;
                    summariesGenerated++;
                    enriched.add(new TrendSignal(
                            signal.keyword(), signal.current30d(), signal.previous90d(),
                            signal.baseline180d(), signal.momentum(), signal.direction(),
                            signal.firstSeenAt(), signal.topArticles(), summary));
                    log.info("generateTrendSummaries() | summary generated for keyword={}",
                             signal.keyword());
                } else {
                    enriched.add(signal);
                }
            } catch (Exception e) {
                log.warn("generateTrendSummaries() | summary failed for keyword={}: {}",
                         signal.keyword(), e.getMessage());
                enriched.add(signal);
            }
        }

        if (!anySummary) {
            log.debug("generateTrendSummaries() | no summaries generated");
            return snapshot;
        }

        TrendSnapshot result = new TrendSnapshot(snapshot.generatedAt(), snapshot.windowDays(),
                enriched, snapshot.fadingTopics(), snapshot.newTopics(), snapshot.totalKeywords());
        log.debug("generateTrendSummaries() | return=enriched with {} summaries", summariesGenerated);
        return result;
    }

    /**
     * Attaches matching articles to any rising signal that has an empty topArticles list.
     * This ensures articles are always visible beneath each trend, even when LLM scoring
     * is disabled or unavailable.
     */
    private TrendSnapshot attachMatchingArticles(TrendSnapshot snapshot, List<NewsArticle> articles) {
        log.debug("attachMatchingArticles() | risingCount={}, articleCount={}",
                  snapshot.risingTopics().size(), articles.size());

        boolean anyEnriched = false;
        List<TrendSignal> enrichedRising = new ArrayList<>();

        for (TrendSignal signal : snapshot.risingTopics()) {
            if (!signal.topArticles().isEmpty()) {
                enrichedRising.add(signal);
                continue;
            }

            // Find articles matching this keyword
            List<ScoredArticle> matched = new ArrayList<>();
            for (NewsArticle article : articles) {
                if (articleContainsKeyword(article, signal.keyword())) {
                    matched.add(new ScoredArticle(
                            article.articleId(),
                            article.title(),
                            5,
                            "Matches trend keyword: " + signal.keyword(),
                            signal.keyword(),
                            article.url() != null ? article.url().toString() : null,
                            article.sourceName(),
                            article.publishedAt()));
                    if (matched.size() >= MAX_ARTICLES_PER_KEYWORD) {
                        break;
                    }
                }
            }

            if (!matched.isEmpty()) {
                anyEnriched = true;
                enrichedRising.add(new TrendSignal(
                        signal.keyword(), signal.current30d(), signal.previous90d(),
                        signal.baseline180d(), signal.momentum(), signal.direction(),
                        signal.firstSeenAt(), matched));
            } else {
                enrichedRising.add(signal);
            }
        }

        if (!anyEnriched) {
            log.debug("attachMatchingArticles() | no signals enriched");
            return snapshot;
        }

        TrendSnapshot result = new TrendSnapshot(snapshot.generatedAt(), snapshot.windowDays(),
                enrichedRising, snapshot.fadingTopics(), snapshot.newTopics(), snapshot.totalKeywords());
        log.debug("attachMatchingArticles() | return=enriched snapshot");
        return result;
    }

    /**
     * Checks if an article's title or body text contains the given keyword (case-insensitive).
     */
    private boolean articleContainsKeyword(NewsArticle article, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        String title = article.title() != null ? article.title().toLowerCase() : "";
        String body = article.bodyText() != null ? article.bodyText().toLowerCase() : "";
        return title.contains(lowerKeyword) || body.contains(lowerKeyword);
    }

    @Override
    public Optional<TrendSnapshot> getLatestSnapshot() {
        log.debug("getLatestSnapshot()");

        Optional<TrendSnapshot> result = trendSnapshotPort.findLatest();

        log.debug("getLatestSnapshot() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }
}
