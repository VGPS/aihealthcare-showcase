package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.infrastructure.delivery.WebhookDispatcher;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduled job that runs trend detection weekly and persists the snapshot.
 *
 * <p>Delegates to {@link DetectTrendsUseCase} which orchestrates LLM-based
 * topic extraction, momentum comparison, article attachment, and persistence.
 *
 * <p>The schedule is externalized to {@code application.yml} via the
 * {@code aihealthcare.trends.schedule} property.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-22
 * @updated 2026-07-29
 */
@Slf4j
@Component
public class TrendDetectionScheduler {

    private final DetectTrendsUseCase detectTrendsUseCase;
    private final WebhookDispatcher webhookDispatcher;
    private final PipelineHealthService healthService;

    public TrendDetectionScheduler(DetectTrendsUseCase detectTrendsUseCase,
                                    @Autowired(required = false) WebhookDispatcher webhookDispatcher,
                                    PipelineHealthService healthService) {
        log.debug("TrendDetectionScheduler() | detectTrendsUseCase={}", detectTrendsUseCase);
        this.detectTrendsUseCase = detectTrendsUseCase;
        this.webhookDispatcher = webhookDispatcher;
        this.healthService = healthService;
    }

    /**
     * Runs trend detection on the configured schedule (default: weekly Sunday 08:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.trends.schedule:0 0 8 ? * SUN}")
    public void runWeeklyTrendDetection() {
        log.debug("runWeeklyTrendDetection()");
        Instant start = Instant.now();

        try {
            TrendSnapshot snapshot = detectTrendsUseCase.detectTrends();

            log.info("runWeeklyTrendDetection() | snapshot saved: rising={}, total={}",
                     snapshot.risingTopics().size(), snapshot.totalKeywords());

            if (webhookDispatcher != null && !snapshot.risingTopics().isEmpty()) {
                try {
                    webhookDispatcher.dispatch(
                            WebhookEventType.TREND_ALERT,
                            snapshot.risingTopics().size() + " Rising Trend" + (snapshot.risingTopics().size() == 1 ? "" : "s") + " Detected",
                            "Weekly trend analysis found " + snapshot.risingTopics().size() + " rising and " + snapshot.fadingTopics().size() + " fading keywords across " + snapshot.totalKeywords() + " tracked terms.",
                            "/dashboard/trends"
                    );
                } catch (Exception we) {
                    log.warn("runWeeklyTrendDetection() | webhook dispatch failed: {}", we.getMessage());
                }
            }
            healthService.recordRun("trend-detection", PipelineHealthService.PipelineRunRecord.success(
                    "trend-detection", snapshot.totalKeywords(), start, Instant.now()));
        } catch (Exception e) {
            log.error("runWeeklyTrendDetection() | trend detection failed", e);
            healthService.recordRun("trend-detection", PipelineHealthService.PipelineRunRecord.failure(
                    "trend-detection", e.getMessage(), start, Instant.now()));
        }

        log.debug("runWeeklyTrendDetection() | return=void");
    }
}
