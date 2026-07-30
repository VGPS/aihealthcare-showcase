package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ExtractedTrend;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSignal;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectLegalTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LegalTrendSnapshotPort;
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
 * Domain service implementing the legal trend detection use case.
 *
 * <p>Orchestrates LLM-based topic extraction specifically for legal, regulatory,
 * and policy articles. The pipeline mirrors {@link TrendOrchestrationService} but
 * is scoped to legal-domain data:
 * <ol>
 *   <li>Fetch recent (30-day) and prior (31-90 day) legal + policy articles</li>
 *   <li>Include regulatory event titles as additional inputs for theme extraction</li>
 *   <li>Call LLM topic extraction on both windows</li>
 *   <li>Compute momentum and classify signals as RISING, NEW, or STABLE</li>
 *   <li>Tag each signal with a category (LITIGATION, REGULATION, POLICY)</li>
 *   <li>Optionally generate summaries for top signals</li>
 *   <li>Persist snapshot via {@link LegalTrendSnapshotPort}</li>
 * </ol>
 *
 * <p>This class carries no Spring annotations — it is wired as a {@code @Bean}
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Slf4j
public class LegalTrendDetectionService implements DetectLegalTrendsUseCase {

    private static final String TOPIC_LEGAL = "AI Healthcare Legal";
    private static final String TOPIC_POLICY = "AI Healthcare Government Policy";
    private static final int RECENT_WINDOW_DAYS = 30;
    private static final int PRIOR_WINDOW_START = 31;
    private static final int PRIOR_WINDOW_END = 90;
    private static final int DEFAULT_MAX_TOPICS = 15;
    private static final int DEFAULT_MAX_SUMMARIES = 3;

    private final ArticleIngestionPort articleIngestionPort;
    private final MonitorRegulatoryEventsUseCase regulatoryUseCase;
    private final TrendTopicExtractionPort trendTopicExtractionPort;
    private final LegalTrendSnapshotPort legalTrendSnapshotPort;
    private final TrendSummaryPort trendSummaryPort;
    private final int maxTopics;

    public LegalTrendDetectionService(ArticleIngestionPort articleIngestionPort,
                                      MonitorRegulatoryEventsUseCase regulatoryUseCase,
                                      TrendTopicExtractionPort trendTopicExtractionPort,
                                      LegalTrendSnapshotPort legalTrendSnapshotPort,
                                      TrendSummaryPort trendSummaryPort,
                                      int maxTopics) {
        log.debug("LegalTrendDetectionService() | articleIngestionPort={}, regulatoryUseCase={}, " +
                  "trendTopicExtractionPort={}, legalTrendSnapshotPort={}, trendSummaryPort={}, maxTopics={}",
                  articleIngestionPort, regulatoryUseCase, trendTopicExtractionPort,
                  legalTrendSnapshotPort, trendSummaryPort, maxTopics);
        this.articleIngestionPort = articleIngestionPort;
        this.regulatoryUseCase = regulatoryUseCase;
        this.trendTopicExtractionPort = trendTopicExtractionPort;
        this.legalTrendSnapshotPort = legalTrendSnapshotPort;
        this.trendSummaryPort = trendSummaryPort;
        this.maxTopics = maxTopics > 0 ? maxTopics : DEFAULT_MAX_TOPICS;
    }

    @Override
    public LegalTrendSnapshot detectLegalTrends() {
        log.debug("detectLegalTrends()");
        Instant now = Instant.now();

        // 1. Fetch recent legal + policy articles (last 30 days)
        List<NewsArticle> recentLegal = articleIngestionPort.fetchByTopicWithArchiveLimit(
                TOPIC_LEGAL, RECENT_WINDOW_DAYS);
        List<NewsArticle> recentPolicy = articleIngestionPort.fetchByTopicWithArchiveLimit(
                TOPIC_POLICY, RECENT_WINDOW_DAYS);

        log.info("detectLegalTrends() | recent articles: legal={}, policy={}",
                 recentLegal.size(), recentPolicy.size());

        // 2. Fetch regulatory events and build pseudo-titles
        List<RegulatoryEvent> regulatoryEvents = regulatoryUseCase.getRecentEvents(50);
        log.info("detectLegalTrends() | regulatory events loaded: {}", regulatoryEvents.size());

        // Combine recent titles + regulatory event titles
        List<String> recentTitles = new ArrayList<>();
        List<String> recentArticleIds = new ArrayList<>();
        List<String> recentCategories = new ArrayList<>();

        for (NewsArticle article : recentLegal) {
            recentTitles.add(article.title());
            recentArticleIds.add(article.articleId());
            recentCategories.add("LITIGATION");
        }
        for (NewsArticle article : recentPolicy) {
            recentTitles.add(article.title());
            recentArticleIds.add(article.articleId());
            recentCategories.add("POLICY");
        }

        Instant recentCutoff = now.minus(RECENT_WINDOW_DAYS, ChronoUnit.DAYS);
        for (RegulatoryEvent event : regulatoryEvents) {
            if (event.discoveredAt().isAfter(recentCutoff)) {
                String pseudoTitle = event.regulatoryBody().name() + ": " + event.title();
                recentTitles.add(pseudoTitle);
                recentArticleIds.add(event.eventId());
                recentCategories.add("REGULATION");
            }
        }

        log.info("detectLegalTrends() | combined recent titles: {}", recentTitles.size());

        // 3. Extract themes from recent window via LLM
        List<ExtractedTrend> recentTrends = List.of();
        if (!recentTitles.isEmpty()) {
            recentTrends = trendTopicExtractionPort.extractTopics(recentTitles, maxTopics);
            log.info("detectLegalTrends() | LLM extracted {} themes from recent window",
                     recentTrends.size());
        }

        // 4. Fetch prior-window articles (31-90 days) for momentum comparison
        List<NewsArticle> allLegal = articleIngestionPort.fetchByTopicWithArchiveLimit(
                TOPIC_LEGAL, PRIOR_WINDOW_END);
        List<NewsArticle> allPolicy = articleIngestionPort.fetchByTopicWithArchiveLimit(
                TOPIC_POLICY, PRIOR_WINDOW_END);

        Instant priorCutoff = now.minus(PRIOR_WINDOW_START, ChronoUnit.DAYS);
        List<String> priorTitles = new ArrayList<>();

        for (NewsArticle article : allLegal) {
            if (article.publishedAt() != null && article.publishedAt().isBefore(priorCutoff)) {
                priorTitles.add(article.title());
            }
        }
        for (NewsArticle article : allPolicy) {
            if (article.publishedAt() != null && article.publishedAt().isBefore(priorCutoff)) {
                priorTitles.add(article.title());
            }
        }
        for (RegulatoryEvent event : regulatoryEvents) {
            if (event.discoveredAt().isBefore(priorCutoff)) {
                priorTitles.add(event.regulatoryBody().name() + ": " + event.title());
            }
        }

        log.info("detectLegalTrends() | prior-window titles: {}", priorTitles.size());

        List<ExtractedTrend> priorTrends = List.of();
        if (!priorTitles.isEmpty()) {
            priorTrends = trendTopicExtractionPort.extractTopics(priorTitles, maxTopics);
            log.info("detectLegalTrends() | LLM extracted {} themes from prior window",
                     priorTrends.size());
        }

        // 5. Build LegalTrendSignal list with momentum
        Set<String> priorLabels = new HashSet<>();
        for (ExtractedTrend pt : priorTrends) {
            priorLabels.add(pt.label().toLowerCase());
        }

        List<LegalTrendSignal> risingSignals = new ArrayList<>();

        for (ExtractedTrend trend : recentTrends) {
            int recentCount = trend.articleIndices().size();
            String labelLower = trend.label().toLowerCase();

            // Determine dominant category from linked article indices
            String category = determineCategory(trend, recentCategories);

            // Find prior count for the same theme
            int priorCount = 0;
            for (ExtractedTrend pt : priorTrends) {
                if (pt.label().toLowerCase().equals(labelLower)) {
                    priorCount = pt.articleIndices().size();
                    break;
                }
            }

            // Collect article IDs from the trend's linked indices
            List<String> topArticleIds = new ArrayList<>();
            for (int index : trend.articleIndices()) {
                if (index >= 0 && index < recentArticleIds.size()) {
                    topArticleIds.add(recentArticleIds.get(index));
                }
            }

            if (!priorLabels.contains(labelLower)) {
                // Brand new theme
                LegalTrendSignal signal = new LegalTrendSignal(
                        trend.label(), category, recentCount, 0,
                        0.0, TrendDirection.NEW, topArticleIds);
                risingSignals.add(signal);
            } else {
                // Theme in both windows — compute momentum
                double momentum = computeMomentum(recentCount, priorCount);
                TrendDirection direction = momentum > 1.5 ? TrendDirection.RISING : TrendDirection.STABLE;

                // Include all classified signals (legal data is sparse; stable
                // themes are still valuable for legal analysts)
                LegalTrendSignal signal = new LegalTrendSignal(
                        trend.label(), category, recentCount, priorCount,
                        momentum, direction, topArticleIds);
                risingSignals.add(signal);
            }
        }

        log.info("detectLegalTrends() | classified {} rising/new signals", risingSignals.size());

        // 6. Optionally generate summaries for top signals
        if (trendSummaryPort != null && trendSummaryPort.isAvailable() && !risingSignals.isEmpty()) {
            risingSignals = generateSummaries(risingSignals);
        }

        // 7. Build and persist snapshot
        LegalTrendSnapshot snapshot = new LegalTrendSnapshot(
                now, RECENT_WINDOW_DAYS, risingSignals, recentTitles.size());

        legalTrendSnapshotPort.save(snapshot);

        log.info("detectLegalTrends() | snapshot saved: signals={}, totalKeywords={}",
                 snapshot.risingTrends().size(), snapshot.totalKeywords());
        log.debug("detectLegalTrends() | return={}", snapshot);
        return snapshot;
    }

    @Override
    public Optional<LegalTrendSnapshot> getLatestSnapshot() {
        log.debug("getLatestSnapshot()");

        Optional<LegalTrendSnapshot> result = legalTrendSnapshotPort.findLatest();

        log.debug("getLatestSnapshot() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    /**
     * Determines the dominant category (LITIGATION, REGULATION, POLICY) for a trend
     * based on the categories of its linked article indices.
     */
    private String determineCategory(ExtractedTrend trend, List<String> categories) {
        log.debug("determineCategory() | label={}, indices={}", trend.label(), trend.articleIndices().size());

        int litigation = 0;
        int regulation = 0;
        int policy = 0;

        for (int index : trend.articleIndices()) {
            if (index >= 0 && index < categories.size()) {
                String cat = categories.get(index);
                if ("LITIGATION".equals(cat)) {
                    litigation++;
                } else if ("REGULATION".equals(cat)) {
                    regulation++;
                } else if ("POLICY".equals(cat)) {
                    policy++;
                }
            }
        }

        String result;
        if (regulation >= litigation && regulation >= policy) {
            result = "REGULATION";
        } else if (litigation >= policy) {
            result = "LITIGATION";
        } else {
            result = "POLICY";
        }

        log.debug("determineCategory() | return={}", result);
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
     * Generates summaries for the top N rising signals using the trend summary port.
     */
    private List<LegalTrendSignal> generateSummaries(List<LegalTrendSignal> signals) {
        log.debug("generateSummaries() | signalCount={}", signals.size());

        int summariesGenerated = 0;
        List<LegalTrendSignal> enriched = new ArrayList<>();

        for (LegalTrendSignal signal : signals) {
            if (summariesGenerated >= DEFAULT_MAX_SUMMARIES) {
                enriched.add(signal);
                continue;
            }

            try {
                // Build minimal article list from IDs for context
                List<NewsArticle> relevant = articleIngestionPort.fetchArticlesByIds(signal.topArticleIds());

                if (relevant.isEmpty()) {
                    enriched.add(signal);
                    continue;
                }

                String summary = trendSummaryPort.generateSummary(signal.keyword(), relevant);
                if (summary != null && !summary.isBlank()) {
                    summariesGenerated++;
                    enriched.add(new LegalTrendSignal(
                            signal.keyword(), signal.category(), signal.current30d(),
                            signal.previous90d(), signal.momentum(), signal.direction(),
                            signal.topArticleIds(), summary));
                    log.info("generateSummaries() | summary generated for keyword={}",
                             signal.keyword());
                } else {
                    enriched.add(signal);
                }
            } catch (Exception e) {
                log.warn("generateSummaries() | summary failed for keyword={}: {}",
                         signal.keyword(), e.getMessage());
                enriched.add(signal);
            }
        }

        log.debug("generateSummaries() | return={} signals, {} with summaries",
                  enriched.size(), summariesGenerated);
        return enriched;
    }
}
