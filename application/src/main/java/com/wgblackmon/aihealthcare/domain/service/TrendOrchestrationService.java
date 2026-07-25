package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
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
 * @updated 2026-07-24
 */
@Slf4j
public class TrendOrchestrationService implements DetectTrendsUseCase {

    private static final int LOOKBACK_DAYS = 7;
    private static final int MAX_ARTICLES_PER_KEYWORD = 5;

    private final ArticleIngestionPort articleIngestionPort;
    private final TrendDetectionService trendDetectionService;
    private final TrendSnapshotPort trendSnapshotPort;
    private final ArticleScoringPort articleScoringPort;
    private final boolean scoringEnabled;
    private final int scoreThreshold;

    public TrendOrchestrationService(ArticleIngestionPort articleIngestionPort,
                                     TrendDetectionService trendDetectionService,
                                     TrendSnapshotPort trendSnapshotPort,
                                     ArticleScoringPort articleScoringPort,
                                     boolean scoringEnabled,
                                     int scoreThreshold) {
        log.debug("TrendOrchestrationService() | articleIngestionPort={}, trendDetectionService={}, " +
                  "trendSnapshotPort={}, articleScoringPort={}, scoringEnabled={}, scoreThreshold={}",
                  articleIngestionPort, trendDetectionService, trendSnapshotPort,
                  articleScoringPort, scoringEnabled, scoreThreshold);
        this.articleIngestionPort = articleIngestionPort;
        this.trendDetectionService = trendDetectionService;
        this.trendSnapshotPort = trendSnapshotPort;
        this.articleScoringPort = articleScoringPort;
        this.scoringEnabled = scoringEnabled;
        this.scoreThreshold = scoreThreshold;
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
