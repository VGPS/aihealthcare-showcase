package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled job that runs the daily AI-healthcare market digest pipeline.
 *
 * <p>Fires daily at 07:00 America/Chicago. The cron schedule is externalized
 * to {@code application.yml} via {@code aihealthcare.market-analysis.schedule}
 * so it can be overridden without code changes (e.g. to adjust for DST or
 * change the time zone in production).
 *
 * <p>Delegates fully to {@link MarketDigestService#generateDailyDigest(LocalDate)},
 * which is idempotent — re-running on the same date returns the existing digest
 * without repeating the pipeline.
 *
 * <p>Exceptions are caught and logged so this thread survives scheduler errors.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class MarketAnalysisScheduler {

    private final MarketDigestService marketDigestService;

    public MarketAnalysisScheduler(MarketDigestService marketDigestService) {
        log.debug("MarketAnalysisScheduler() | marketDigestService={}", marketDigestService);
        this.marketDigestService = marketDigestService;
        log.debug("MarketAnalysisScheduler() | return=void");
    }

    /**
     * Runs the daily market digest pipeline at 07:00 America/Chicago.
     *
     * <p>The default cron is {@code 0 0 7 * * *} (every day at 07:00).
     * The {@code zone} attribute pins execution to Chicago time so the
     * trigger is unaffected by the server's local timezone.
     */
    @Scheduled(cron = "${aihealthcare.market-analysis.schedule:0 0 7 * * *}",
               zone  = "America/Chicago")
    public void runDailyDigest() {
        log.debug("runDailyDigest()");
        LocalDate today = LocalDate.now();
        log.info("runDailyDigest() | starting market digest pipeline for {}", today);

        try {
            MarketDigest digest = marketDigestService.generateDailyDigest(today);
            log.info("runDailyDigest() | digest complete — {} qualifying entries for {}",
                    digest.entries().size(), today);
        } catch (Exception e) {
            log.error("runDailyDigest() | pipeline failed for {}", today, e);
        }

        log.debug("runDailyDigest() | return=void");
    }
}
