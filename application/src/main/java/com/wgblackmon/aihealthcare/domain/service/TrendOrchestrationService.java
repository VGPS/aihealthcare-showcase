package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ExtractedTrend;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSummaryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendTopicExtractionPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Application-layer service implementing the trend detection use case.
 *
 * <p>Orchestrates LLM-based topic extraction, momentum comparison between
 * recent and prior time windows, article attachment, and snapshot persistence.
 *
 * <p>The pipeline:
 * <ol>
 *   <li>Fetch recent (30-day) articles and extract themes via LLM</li>
 *   <li>Fetch prior (31-90 day) articles and extract themes for comparison</li>
 *   <li>Build TrendSignal list: themes appearing in recent but not prior are NEW,
 *       themes in both get momentum from article count ratio</li>
 *   <li>Attach matching articles to each signal</li>
 *   <li>Optionally score articles via LLM for significance</li>
 *   <li>Optionally generate deep-research summaries for top signals</li>
 *   <li>Persist snapshot</li>
 * </ol>
 *
 * <p>This class carries no Spring annotations — it is wired as a {@code @Bean}
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-22
 * @updated 2026-07-29
 */
@Slf4j
public class TrendOrchestrationService implements DetectTrendsUseCase {

    private static final int RECENT_WINDOW_DAYS = 30;
    private static final int PRIOR_WINDOW_START = 31;
    private static final int PRIOR_WINDOW_END = 90;
    private static final int DEFAULT_MAX_TOPICS = 20;
    private static final int DEFAULT_MAX_SUMMARIES = 5;

    private final ArticleIngestionPort articleIngestionPort;
    private final TrendTopicExtractionPort trendTopicExtractionPort;
    private final TrendSnapshotPort trendSnapshotPort;
    private final ArticleScoringPort articleScoringPort;
    private final TrendSummaryPort trendSummaryPort;
    private final boolean scoringEnabled;
    private final int scoreThreshold;
    private final int maxSummariesPerRun;
    private final int maxTopics;

    public TrendOrchestrationService(ArticleIngestionPort articleIngestionPort,
                                     TrendTopicExtractionPort trendTopicExtractionPort,
                                     TrendSnapshotPort trendSnapshotPort,
                                     ArticleScoringPort articleScoringPort,
                                     TrendSummaryPort trendSummaryPort,
                                     boolean scoringEnabled,
                                     int scoreThreshold,
                                     int maxSummariesPerRun,
                                     int maxTopics) {
        log.debug("TrendOrchestrationService() | articleIngestionPort={}, trendTopicExtractionPort={}, " +
                  "trendSnapshotPort={}, articleScoringPort={}, trendSummaryPort={}, " +
                  "scoringEnabled={}, scoreThreshold={}, maxSummariesPerRun={}, maxTopics={}",
                  articleIngestionPort, trendTopicExtractionPort, trendSnapshotPort,
                  articleScoringPort, trendSummaryPort, scoringEnabled, scoreThreshold,
                  maxSummariesPerRun, maxTopics);
        this.articleIngestionPort = articleIngestionPort;
        this.trendTopicExtractionPort = trendTopicExtractionPort;
        this.trendSnapshotPort = trendSnapshotPort;
        this.articleScoringPort = articleScoringPort;
        this.trendSummaryPort = trendSummaryPort;
        this.scoringEnabled = scoringEnabled;
        this.scoreThreshold = scoreThreshold;
        this.maxSummariesPerRun = maxSummariesPerRun > 0 ? maxSummariesPerRun : DEFAULT_MAX_SUMMARIES;
        this.maxTopics = maxTopics > 0 ? maxTopics : DEFAULT_MAX_TOPICS;
    }

    @Override
    public TrendSnapshot detectTrends() {
        log.debug("detectTrends()");
        Instant now = Instant.now();

        // 1. Fetch recent articles (last 30 days) and extract themes
        List<NewsArticle> recentArticles = articleIngestionPort.fetchRecentArticles(RECENT_WINDOW_DAYS);
        log.info("detectTrends() | loaded {} recent articles from last {} days",
                 recentArticles.size(), RECENT_WINDOW_DAYS);

        List<String> recentTitles = new ArrayList<>();
        for (NewsArticle article : recentArticles) {
            recentTitles.add(article.title());
        }

        List<ExtractedTrend> recentTrends = List.of();
        if (!recentTitles.isEmpty()) {
            recentTrends = trendTopicExtractionPort.extractTopics(recentTitles, maxTopics);
            log.info("detectTrends() | LLM extracted {} themes from recent articles",
                     recentTrends.size());
        }

        // 2. Fetch prior-window articles (31-90 days) and extract themes for momentum
        List<NewsArticle> allArticles = articleIngestionPort.fetchRecentArticles(PRIOR_WINDOW_END);
        Instant recentCutoff = now.minus(PRIOR_WINDOW_START, ChronoUnit.DAYS);
        List<NewsArticle> priorArticles = new ArrayList<>();
        for (NewsArticle article : allArticles) {
            if (article.publishedAt() != null && article.publishedAt().isBefore(recentCutoff)) {
                priorArticles.add(article);
            }
        }
        log.info("detectTrends() | loaded {} prior-window articles (days {}-{})",
                 priorArticles.size(), PRIOR_WINDOW_START, PRIOR_WINDOW_END);

        List<String> priorTitles = new ArrayList<>();
        for (NewsArticle article : priorArticles) {
            priorTitles.add(article.title());
        }

        List<ExtractedTrend> priorTrends = List.of();
        if (!priorTitles.isEmpty()) {
            priorTrends = trendTopicExtractionPort.extractTopics(priorTitles, maxTopics);
            log.info("detectTrends() | LLM extracted {} themes from prior-window articles",
                     priorTrends.size());
        }

        // 3. Build TrendSignal list with momentum
        Set<String> priorLabels = new HashSet<>();
        for (ExtractedTrend pt : priorTrends) {
            priorLabels.add(pt.label().toLowerCase());
        }

        List<TrendSignal> risingSignals = new ArrayList<>();
        List<TrendSignal> newSignals = new ArrayList<>();

        for (ExtractedTrend trend : recentTrends) {
            int recentCount = trend.articleIndices().size();
            String labelLower = trend.label().toLowerCase();

            // Find how many prior articles matched a similar theme
            int priorCount = 0;
            for (ExtractedTrend pt : priorTrends) {
                if (pt.label().toLowerCase().equals(labelLower)) {
                    priorCount = pt.articleIndices().size();
                    break;
                }
            }

            // Attach matching articles from the recent set
            List<ScoredArticle> topArticles = buildArticlesForTrend(trend, recentArticles);

            if (!priorLabels.contains(labelLower)) {
                // Brand new theme — not seen in prior window
                TrendSignal signal = new TrendSignal(
                        trend.label(), recentCount, 0, 0,
                        0.0, TrendDirection.NEW, now, topArticles);
                newSignals.add(signal);
            } else {
                // Theme exists in both windows — compute momentum
                double momentum = computeMomentum(recentCount, priorCount);
                TrendDirection direction = momentum > 1.5 ? TrendDirection.RISING : TrendDirection.STABLE;

                if (direction == TrendDirection.RISING) {
                    TrendSignal signal = new TrendSignal(
                            trend.label(), recentCount, priorCount, 0,
                            momentum, direction, now, topArticles);
                    risingSignals.add(signal);
                }
            }
        }

        log.info("detectTrends() | classified: {} rising, {} new",
                 risingSignals.size(), newSignals.size());

        // Combine rising + new as all "rising" for display (both are noteworthy)
        List<TrendSignal> allRising = new ArrayList<>();
        for (TrendSignal s : risingSignals) {
            allRising.add(s);
        }
        for (TrendSignal s : newSignals) {
            allRising.add(s);
        }

        TrendSnapshot snapshot = new TrendSnapshot(
                now, RECENT_WINDOW_DAYS, allRising, List.of(), List.of(),
                recentTrends.size());

        // 4. Score articles via LLM (if enabled)
        if (scoringEnabled && articleScoringPort != null && !snapshot.risingTopics().isEmpty()) {
            snapshot = scoreRisingTopics(snapshot, recentArticles);
        }

        // 5. Generate deep research summaries for top rising topics
        if (trendSummaryPort != null && trendSummaryPort.isAvailable()
                && !snapshot.risingTopics().isEmpty()) {
            snapshot = generateTrendSummaries(snapshot, recentArticles);
        }

        trendSnapshotPort.save(snapshot);

        log.info("detectTrends() | snapshot saved: rising={}, total={}",
                 snapshot.risingTopics().size(), snapshot.totalKeywords());
        log.debug("detectTrends() | return={}", snapshot);
        return snapshot;
    }

    /**
     * Builds ScoredArticle list from an ExtractedTrend's article indices.
     */
    private List<ScoredArticle> buildArticlesForTrend(ExtractedTrend trend,
                                                       List<NewsArticle> articles) {
        log.debug("buildArticlesForTrend() | label={}, indexCount={}",
                  trend.label(), trend.articleIndices().size());

        List<ScoredArticle> result = new ArrayList<>();

        for (int index : trend.articleIndices()) {
            if (index < 0 || index >= articles.size()) {
                continue;
            }

            NewsArticle article = articles.get(index);
            result.add(new ScoredArticle(
                    article.articleId(),
                    article.title(),
                    5, // default score before LLM scoring
                    trend.description(),
                    trend.label(),
                    article.url() != null ? article.url().toString() : null,
                    article.sourceName(),
                    article.publishedAt()));
        }

        log.debug("buildArticlesForTrend() | return={} articles", result.size());
        return result;
    }

    /**
     * Computes momentum as the ratio of per-day frequency between windows.
     * Recent window = 30 days, prior window = 60 days (31-90).
     */
    private double computeMomentum(int recentCount, int priorCount) {
        if (priorCount == 0) {
            return recentCount > 0 ? 10.0 : 0.0;
        }
        double recentPerDay = (double) recentCount / RECENT_WINDOW_DAYS;
        double priorPerDay = (double) priorCount / (PRIOR_WINDOW_END - PRIOR_WINDOW_START + 1);
        return recentPerDay / priorPerDay;
    }

    /**
     * Scores articles for each rising topic by calling the LLM scoring port.
     */
    private TrendSnapshot scoreRisingTopics(TrendSnapshot snapshot, List<NewsArticle> allArticles) {
        log.debug("scoreRisingTopics() | risingCount={}", snapshot.risingTopics().size());

        boolean anyEnriched = false;
        List<TrendSignal> enriched = new ArrayList<>();

        for (TrendSignal signal : snapshot.risingTopics()) {
            // Use the articles already linked by the LLM during extraction
            List<ScoredArticle> attached = signal.topArticles();
            if (attached == null || attached.isEmpty()) {
                enriched.add(signal);
                continue;
            }

            // Convert attached ScoredArticles back to NewsArticle for the scoring port
            List<NewsArticle> relevantArticles = new ArrayList<>();
            for (ScoredArticle sa : attached) {
                for (NewsArticle article : allArticles) {
                    if (article.articleId().equals(sa.articleId())) {
                        relevantArticles.add(article);
                        break;
                    }
                }
            }

            if (relevantArticles.isEmpty()) {
                enriched.add(signal);
                continue;
            }

            try {
                List<ScoredArticle> scored = articleScoringPort.scoreArticles(
                        relevantArticles, signal.keyword(),
                        "Healthcare AI trend: " + signal.keyword(),
                        scoreThreshold);

                if (!scored.isEmpty()) {
                    anyEnriched = true;
                    enriched.add(new TrendSignal(
                            signal.keyword(), signal.current30d(), signal.previous90d(),
                            signal.baseline180d(), signal.momentum(), signal.direction(),
                            signal.firstSeenAt(), scored, signal.summary()));
                } else {
                    enriched.add(signal);
                }
            } catch (Exception e) {
                log.warn("scoreRisingTopics() | scoring failed for topic={}: {}",
                         signal.keyword(), e.getMessage());
                enriched.add(signal);
            }
        }

        if (!anyEnriched) {
            log.debug("scoreRisingTopics() | no topics enriched with scores");
            return snapshot;
        }

        TrendSnapshot result = new TrendSnapshot(snapshot.generatedAt(), snapshot.windowDays(),
                enriched, snapshot.fadingTopics(), snapshot.newTopics(), snapshot.totalKeywords());
        log.debug("scoreRisingTopics() | return=enriched snapshot");
        return result;
    }

    /**
     * Generates deep-research trend summaries for the top N rising topics
     * (capped by {@code maxSummariesPerRun} for cost control).
     */
    private TrendSnapshot generateTrendSummaries(TrendSnapshot snapshot, List<NewsArticle> articles) {
        log.debug("generateTrendSummaries() | risingCount={}, maxSummaries={}",
                  snapshot.risingTopics().size(), maxSummariesPerRun);

        boolean anySummary = false;
        int summariesGenerated = 0;
        List<TrendSignal> enriched = new ArrayList<>();

        for (TrendSignal signal : snapshot.risingTopics()) {
            if (summariesGenerated >= maxSummariesPerRun) {
                enriched.add(signal);
                continue;
            }

            // Use articles already linked by the LLM during extraction
            List<NewsArticle> relevant = new ArrayList<>();
            for (ScoredArticle sa : signal.topArticles()) {
                for (NewsArticle article : articles) {
                    if (article.articleId().equals(sa.articleId())) {
                        relevant.add(article);
                        break;
                    }
                }
                if (relevant.size() >= 10) {
                    break;
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
                    log.info("generateTrendSummaries() | summary generated for topic={}",
                             signal.keyword());
                } else {
                    enriched.add(signal);
                }
            } catch (Exception e) {
                log.warn("generateTrendSummaries() | summary failed for topic={}: {}",
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

    @Override
    public Optional<TrendSnapshot> getLatestSnapshot() {
        log.debug("getLatestSnapshot()");

        Optional<TrendSnapshot> result = trendSnapshotPort.findLatest();

        log.debug("getLatestSnapshot() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<TrendSnapshot> getAllSnapshots() {
        log.debug("getAllSnapshots()");

        List<TrendSnapshot> result = trendSnapshotPort.findAll();

        log.debug("getAllSnapshots() | return={} snapshots", result.size());
        return result;
    }
}
