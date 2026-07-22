package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
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
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
public class TrendOrchestrationService implements DetectTrendsUseCase {

    private static final int LOOKBACK_DAYS = 7;

    private final ArticleIngestionPort articleIngestionPort;
    private final TrendDetectionService trendDetectionService;
    private final TrendSnapshotPort trendSnapshotPort;

    public TrendOrchestrationService(ArticleIngestionPort articleIngestionPort,
                                     TrendDetectionService trendDetectionService,
                                     TrendSnapshotPort trendSnapshotPort) {
        log.debug("TrendOrchestrationService() | articleIngestionPort={}, trendDetectionService={}, trendSnapshotPort={}",
                  articleIngestionPort, trendDetectionService, trendSnapshotPort);
        this.articleIngestionPort = articleIngestionPort;
        this.trendDetectionService = trendDetectionService;
        this.trendSnapshotPort = trendSnapshotPort;
    }

    @Override
    public TrendSnapshot detectTrends() {
        log.debug("detectTrends()");

        List<NewsArticle> articles = articleIngestionPort.fetchRecentArticles(LOOKBACK_DAYS);
        log.info("detectTrends() | loaded {} articles from last {} days", articles.size(), LOOKBACK_DAYS);

        TrendSnapshot snapshot = trendDetectionService.detectTrends(articles, Instant.now());
        trendSnapshotPort.save(snapshot);

        log.info("detectTrends() | snapshot saved: rising={}, fading={}, new={}, total={}",
                 snapshot.risingTopics().size(), snapshot.fadingTopics().size(),
                 snapshot.newTopics().size(), snapshot.totalKeywords());
        log.debug("detectTrends() | return={}", snapshot);
        return snapshot;
    }

    @Override
    public Optional<TrendSnapshot> getLatestSnapshot() {
        log.debug("getLatestSnapshot()");

        Optional<TrendSnapshot> result = trendSnapshotPort.findLatest();

        log.debug("getLatestSnapshot() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }
}
