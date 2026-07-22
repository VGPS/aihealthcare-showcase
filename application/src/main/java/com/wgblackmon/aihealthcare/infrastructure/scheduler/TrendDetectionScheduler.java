package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.service.TrendDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that runs trend detection weekly and persists the snapshot.
 *
 * <p>Loads all articles from the last 180 days, delegates to
 * {@link TrendDetectionService} for frequency analysis, and saves the
 * resulting {@link TrendSnapshot} via {@link TrendSnapshotPort}.
 *
 * <p>The schedule is externalized to {@code application.yml} via the
 * {@code aihealthcare.trends.schedule} property.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Component
public class TrendDetectionScheduler {

    private static final int LOOKBACK_DAYS = 180;

    private final ArticleIngestionPort articleIngestionPort;
    private final TrendDetectionService trendDetectionService;
    private final TrendSnapshotPort trendSnapshotPort;

    public TrendDetectionScheduler(ArticleIngestionPort articleIngestionPort,
                                   TrendDetectionService trendDetectionService,
                                   TrendSnapshotPort trendSnapshotPort) {
        log.debug("TrendDetectionScheduler() | articleIngestionPort={}, trendDetectionService={}, trendSnapshotPort={}",
                  articleIngestionPort, trendDetectionService, trendSnapshotPort);
        this.articleIngestionPort = articleIngestionPort;
        this.trendDetectionService = trendDetectionService;
        this.trendSnapshotPort = trendSnapshotPort;
    }

    /**
     * Runs trend detection on the configured schedule (default: weekly Sunday 08:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.trends.schedule:0 0 8 ? * SUN}")
    public void runWeeklyTrendDetection() {
        log.debug("runWeeklyTrendDetection()");

        try {
            List<NewsArticle> articles = articleIngestionPort.fetchRecentArticles(LOOKBACK_DAYS);
            log.info("runWeeklyTrendDetection() | loaded {} articles from last {} days",
                     articles.size(), LOOKBACK_DAYS);

            TrendSnapshot snapshot = trendDetectionService.detectTrends(articles, Instant.now());
            trendSnapshotPort.save(snapshot);

            log.info("runWeeklyTrendDetection() | snapshot saved: rising={}, fading={}, new={}, total={}",
                     snapshot.risingTopics().size(), snapshot.fadingTopics().size(),
                     snapshot.newTopics().size(), snapshot.totalKeywords());
        } catch (Exception e) {
            log.error("runWeeklyTrendDetection() | trend detection failed", e);
        }

        log.debug("runWeeklyTrendDetection() | return=void");
    }
}
