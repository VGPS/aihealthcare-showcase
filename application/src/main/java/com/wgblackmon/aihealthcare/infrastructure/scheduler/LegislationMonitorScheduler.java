package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that checks legislation source URLs for content changes.
 *
 * <p>Runs weekly on Monday at 10:30 UTC (5:30 AM Chicago). Fetches each
 * source URL in the registry, computes a SHA-256 hash, and records a
 * {@link com.wgblackmon.aihealthcare.domain.model.LawChangeEvent} when
 * content has changed since the last check.
 *
 * <p>The cron expression is externalized to {@code application.yml} via
 * {@code aihealthcare.legislation.source-check-schedule}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
@Slf4j
@Component
public class LegislationMonitorScheduler {

    private final ManageStateLawsUseCase manageStateLawsUseCase;

    public LegislationMonitorScheduler(ManageStateLawsUseCase manageStateLawsUseCase) {
        log.debug("LegislationMonitorScheduler() | manageStateLawsUseCase={}",
                  manageStateLawsUseCase.getClass().getSimpleName());
        this.manageStateLawsUseCase = manageStateLawsUseCase;
    }

    /**
     * Weekly source-freshness check. Re-fetches all law source URLs and
     * records change events for any content that has drifted.
     */
    @Scheduled(cron = "${aihealthcare.legislation.source-check-schedule}")
    public void runSourceFreshnessCheck() {
        log.debug("runSourceFreshnessCheck()");
        try {
            int changedCount = manageStateLawsUseCase.triggerRefresh();
            log.info("LegislationMonitorScheduler | source check complete — {} sources changed", changedCount);
        } catch (Exception e) {
            log.error("runSourceFreshnessCheck() | failed: {}", e.getMessage(), e);
        }
        log.debug("runSourceFreshnessCheck() | return=void");
    }
}
