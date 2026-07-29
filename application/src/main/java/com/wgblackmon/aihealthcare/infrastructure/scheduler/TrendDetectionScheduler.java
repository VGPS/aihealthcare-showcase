package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    public TrendDetectionScheduler(DetectTrendsUseCase detectTrendsUseCase) {
        log.debug("TrendDetectionScheduler() | detectTrendsUseCase={}", detectTrendsUseCase);
        this.detectTrendsUseCase = detectTrendsUseCase;
    }

    /**
     * Runs trend detection on the configured schedule (default: weekly Sunday 08:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.trends.schedule:0 0 8 ? * SUN}")
    public void runWeeklyTrendDetection() {
        log.debug("runWeeklyTrendDetection()");

        try {
            TrendSnapshot snapshot = detectTrendsUseCase.detectTrends();

            log.info("runWeeklyTrendDetection() | snapshot saved: rising={}, total={}",
                     snapshot.risingTopics().size(), snapshot.totalKeywords());
        } catch (Exception e) {
            log.error("runWeeklyTrendDetection() | trend detection failed", e);
        }

        log.debug("runWeeklyTrendDetection() | return=void");
    }
}
