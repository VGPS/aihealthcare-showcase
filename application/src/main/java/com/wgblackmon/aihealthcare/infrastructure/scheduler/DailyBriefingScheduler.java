package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.service.DailyBriefingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the personalized daily briefing pipeline.
 *
 * <p>Runs daily at the time configured by
 * {@code aihealthcare.briefing.schedule} (default 07:30 UTC),
 * after the main harvest and embedding pipelines have completed.
 *
 * <p>Delegates to {@link DailyBriefingService} for all orchestration.
 * Top-level try/catch ensures the scheduler thread survives failures.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class DailyBriefingScheduler {

    private final DailyBriefingService dailyBriefingService;

    public DailyBriefingScheduler(DailyBriefingService dailyBriefingService) {
        log.debug("DailyBriefingScheduler() | dailyBriefingService={}",
                dailyBriefingService.getClass().getSimpleName());
        this.dailyBriefingService = dailyBriefingService;
    }

    @Scheduled(cron = "${aihealthcare.briefing.schedule}")
    public void runDailyBriefing() {
        log.debug("runDailyBriefing() | starting personalized daily briefing");
        try {
            dailyBriefingService.sendDailyBriefings();
        } catch (Exception ex) {
            log.error("runDailyBriefing() | Daily briefing pipeline failed: {}", ex.getMessage(), ex);
        }
        log.debug("runDailyBriefing() | return=void");
    }
}
