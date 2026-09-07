package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled jobs for the AI-healthcare market digest pipeline.
 *
 * <p>{@link #runDailyDigest()} fires daily at 07:00 America/Chicago and delegates
 * fully to {@link MarketDigestService#generateDailyDigest(LocalDate)}, which is
 * idempotent — re-running on the same date returns the existing digest without
 * repeating the pipeline.
 *
 * <p>{@link #runReactionCapture()} fires hourly and delegates to
 * {@link PriceReactionService#capturePendingReactions(Instant)}, which is also
 * idempotent — it only measures horizons that are due and not yet captured, so
 * running it frequently is safe.
 *
 * <p>Both cron schedules are externalized to {@code application.yml} so they can
 * be overridden without code changes. Exceptions are caught and logged so this
 * thread survives scheduler errors.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-07
 */
@Slf4j
@Component
public class MarketAnalysisScheduler {

    private final ProduceMarketDigestUseCase marketDigestService;
    private final PriceReactionService priceReactionService;

    public MarketAnalysisScheduler(ProduceMarketDigestUseCase marketDigestService,
                                    PriceReactionService priceReactionService) {
        log.debug("MarketAnalysisScheduler() | marketDigestService={}, priceReactionService={}",
                marketDigestService, priceReactionService);
        this.marketDigestService = marketDigestService;
        this.priceReactionService = priceReactionService;
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

    /**
     * Polls for any due, uncaptured price-reaction horizons across recent digest
     * entries. The default cron is {@code 0 0 * * * *} (top of every hour).
     */
    @Scheduled(cron = "${aihealthcare.market-analysis.reaction.schedule:0 0 * * * *}")
    public void runReactionCapture() {
        log.debug("runReactionCapture()");
        Instant now = Instant.now();

        try {
            List<PriceReactionSnapshot> captured = priceReactionService.capturePendingReactions(now);
            log.info("runReactionCapture() | captured {} new price-reaction snapshots", captured.size());
        } catch (Exception e) {
            log.error("runReactionCapture() | polling failed", e);
        }

        log.debug("runReactionCapture() | return=void");
    }
}
