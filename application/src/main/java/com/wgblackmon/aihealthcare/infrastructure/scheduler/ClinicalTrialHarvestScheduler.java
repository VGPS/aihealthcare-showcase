package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.service.ClinicalTrialWatchlistMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled job that harvests clinical trials daily from ClinicalTrials.gov
 * and matches them against subscriber watchlists.
 *
 * <p>Runs at 05:00 UTC (after regulatory harvest, before competitor scraping).
 * Each run: harvests → deduplicates by NCT ID → saves new trials → matches
 * against watchlist items → saves new matches.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@Slf4j
@Component
public class ClinicalTrialHarvestScheduler {

    private final ClinicalTrialHarvestingPort clinicalTrialHarvestingPort;
    private final ClinicalTrialPort clinicalTrialPort;
    private final WatchlistPort watchlistPort;
    private final WatchlistMatchPort watchlistMatchPort;
    private final ClinicalTrialWatchlistMatcher clinicalTrialWatchlistMatcher;

    public ClinicalTrialHarvestScheduler(ClinicalTrialHarvestingPort clinicalTrialHarvestingPort,
                                          ClinicalTrialPort clinicalTrialPort,
                                          WatchlistPort watchlistPort,
                                          WatchlistMatchPort watchlistMatchPort,
                                          ClinicalTrialWatchlistMatcher clinicalTrialWatchlistMatcher) {
        log.debug("ClinicalTrialHarvestScheduler() | clinicalTrialHarvestingPort={}, clinicalTrialPort={}, " +
                  "watchlistPort={}, watchlistMatchPort={}, clinicalTrialWatchlistMatcher={}",
                  clinicalTrialHarvestingPort, clinicalTrialPort,
                  watchlistPort, watchlistMatchPort, clinicalTrialWatchlistMatcher);
        this.clinicalTrialHarvestingPort = clinicalTrialHarvestingPort;
        this.clinicalTrialPort = clinicalTrialPort;
        this.watchlistPort = watchlistPort;
        this.watchlistMatchPort = watchlistMatchPort;
        this.clinicalTrialWatchlistMatcher = clinicalTrialWatchlistMatcher;
    }

    /**
     * Runs clinical trial harvest on the configured schedule
     * (default: daily at 05:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.clinical-trials.schedule:0 0 5 * * *}")
    public void runDailyClinicalTrialHarvest() {
        log.debug("runDailyClinicalTrialHarvest()");

        try {
            // Step 1: Harvest from ClinicalTrials.gov
            List<ClinicalTrial> harvested = clinicalTrialHarvestingPort.harvestAll();
            log.info("runDailyClinicalTrialHarvest() | harvested {} raw trials", harvested.size());

            // Step 2: Deduplicate by NCT ID and save
            List<ClinicalTrial> newTrials = new ArrayList<>();
            for (ClinicalTrial trial : harvested) {
                if (!clinicalTrialPort.existsByNctId(trial.nctId())) {
                    newTrials.add(trial);
                }
            }

            if (!newTrials.isEmpty()) {
                clinicalTrialPort.saveAll(newTrials);
                log.info("runDailyClinicalTrialHarvest() | saved {} new trials", newTrials.size());
            }

            // Step 3: Match new trials against watchlists
            List<WatchlistItem> allItems = watchlistPort.findAll();
            if (!allItems.isEmpty() && !newTrials.isEmpty()) {
                List<WatchlistMatch> matches = clinicalTrialWatchlistMatcher
                        .matchTrialsAgainstWatchlist(newTrials, allItems);

                // Deduplicate against existing matches
                List<WatchlistMatch> freshMatches = new ArrayList<>();
                for (WatchlistMatch match : matches) {
                    if (!watchlistMatchPort.existsByItemAndArticle(match.itemId(), match.articleId())) {
                        freshMatches.add(match);
                    }
                }

                if (!freshMatches.isEmpty()) {
                    watchlistMatchPort.saveAll(freshMatches);
                    log.info("runDailyClinicalTrialHarvest() | saved {} watchlist matches", freshMatches.size());
                }
            }

        } catch (Exception e) {
            log.error("runDailyClinicalTrialHarvest() | clinical trial harvest failed", e);
        }

        log.debug("runDailyClinicalTrialHarvest() | return=void");
    }
}
