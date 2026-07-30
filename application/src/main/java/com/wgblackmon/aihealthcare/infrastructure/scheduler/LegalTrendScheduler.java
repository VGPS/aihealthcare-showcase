package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectLegalTrendsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that runs legal trend detection weekly and persists the snapshot.
 *
 * <p>Delegates to {@link DetectLegalTrendsUseCase} which orchestrates LLM-based
 * topic extraction, momentum comparison, and persistence specifically for
 * legal, regulatory, and policy articles.
 *
 * <p>The schedule is externalized to {@code application.yml} via the
 * {@code aihealthcare.legal-trends.schedule} property. Defaults to Sunday
 * 09:00 UTC (one hour after general trend detection).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Slf4j
@Component
public class LegalTrendScheduler {

    private final DetectLegalTrendsUseCase detectLegalTrendsUseCase;

    public LegalTrendScheduler(DetectLegalTrendsUseCase detectLegalTrendsUseCase) {
        log.debug("LegalTrendScheduler() | detectLegalTrendsUseCase={}", detectLegalTrendsUseCase);
        this.detectLegalTrendsUseCase = detectLegalTrendsUseCase;
    }

    /**
     * Runs legal trend detection on the configured schedule (default: weekly Sunday 09:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.legal-trends.schedule:0 0 9 ? * SUN}")
    public void runWeeklyLegalTrendDetection() {
        log.debug("runWeeklyLegalTrendDetection()");

        try {
            LegalTrendSnapshot snapshot = detectLegalTrendsUseCase.detectLegalTrends();

            log.info("runWeeklyLegalTrendDetection() | snapshot saved: signals={}, total={}",
                     snapshot.risingTrends().size(), snapshot.totalKeywords());
        } catch (Exception e) {
            log.error("runWeeklyLegalTrendDetection() | legal trend detection failed", e);
        }

        log.debug("runWeeklyLegalTrendDetection() | return=void");
    }
}
