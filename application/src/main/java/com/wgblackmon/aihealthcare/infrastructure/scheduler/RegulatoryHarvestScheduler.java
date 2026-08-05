package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.service.RegulatoryWatchlistMatcher;
import com.wgblackmon.aihealthcare.infrastructure.delivery.WebhookDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled job that harvests regulatory events daily and matches them
 * against subscriber watchlists.
 *
 * <p>Runs at 04:30 UTC (after RSS feeds, before competitor scraping).
 * Each run: harvests → deduplicates → saves new events → matches
 * against watchlist items → saves new matches.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Component
public class RegulatoryHarvestScheduler {

    private final RegulatoryHarvestingPort regulatoryHarvestingPort;
    private final RegulatoryEventPort regulatoryEventPort;
    private final WatchlistPort watchlistPort;
    private final WatchlistMatchPort watchlistMatchPort;
    private final RegulatoryWatchlistMatcher regulatoryWatchlistMatcher;
    private final WebhookDispatcher webhookDispatcher;

    public RegulatoryHarvestScheduler(RegulatoryHarvestingPort regulatoryHarvestingPort,
                                      RegulatoryEventPort regulatoryEventPort,
                                      WatchlistPort watchlistPort,
                                      WatchlistMatchPort watchlistMatchPort,
                                      RegulatoryWatchlistMatcher regulatoryWatchlistMatcher,
                                      @Autowired(required = false) WebhookDispatcher webhookDispatcher) {
        log.debug("RegulatoryHarvestScheduler() | regulatoryHarvestingPort={}, regulatoryEventPort={}, " +
                  "watchlistPort={}, watchlistMatchPort={}, regulatoryWatchlistMatcher={}",
                  regulatoryHarvestingPort, regulatoryEventPort,
                  watchlistPort, watchlistMatchPort, regulatoryWatchlistMatcher);
        this.regulatoryHarvestingPort = regulatoryHarvestingPort;
        this.regulatoryEventPort = regulatoryEventPort;
        this.watchlistPort = watchlistPort;
        this.watchlistMatchPort = watchlistMatchPort;
        this.regulatoryWatchlistMatcher = regulatoryWatchlistMatcher;
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * Runs regulatory event harvest on the configured schedule
     * (default: daily at 04:30 UTC).
     */
    @Scheduled(cron = "${aihealthcare.regulatory.schedule:0 30 4 * * *}")
    public void runDailyRegulatoryHarvest() {
        log.debug("runDailyRegulatoryHarvest()");

        try {
            // Step 1: Harvest from all sources
            List<RegulatoryEvent> harvested = regulatoryHarvestingPort.harvestAll();
            log.info("runDailyRegulatoryHarvest() | harvested {} raw events", harvested.size());

            // Step 2: Deduplicate and save
            List<RegulatoryEvent> newEvents = new ArrayList<>();
            for (RegulatoryEvent event : harvested) {
                boolean duplicate = false;
                if (event.referenceNumber() != null && !event.referenceNumber().isBlank()) {
                    duplicate = regulatoryEventPort.existsByReferenceNumber(event.referenceNumber());
                }
                if (!duplicate) {
                    duplicate = regulatoryEventPort.existsBySourceUrl(event.sourceUrl());
                }
                if (!duplicate) {
                    newEvents.add(event);
                }
            }

            if (!newEvents.isEmpty()) {
                regulatoryEventPort.saveAll(newEvents);
                log.info("runDailyRegulatoryHarvest() | saved {} new events", newEvents.size());
                if (webhookDispatcher != null) {
                    try {
                        webhookDispatcher.dispatch(
                                WebhookEventType.REGULATORY_ALERT,
                                newEvents.size() + " New Regulatory Event" + (newEvents.size() == 1 ? "" : "s"),
                                newEvents.size() + " new regulatory event" + (newEvents.size() == 1 ? " was" : "s were") + " detected from FDA/CMS sources.",
                                "/dashboard/regulatory"
                        );
                    } catch (Exception e) {
                        log.warn("runDailyRegulatoryHarvest() | webhook dispatch failed: {}", e.getMessage());
                    }
                }
            }

            // Step 3: Match new events against watchlists
            List<WatchlistItem> allItems = watchlistPort.findAll();
            if (!allItems.isEmpty() && !newEvents.isEmpty()) {
                List<WatchlistMatch> matches = regulatoryWatchlistMatcher
                        .matchEventsAgainstWatchlist(newEvents, allItems);

                // Deduplicate against existing matches
                List<WatchlistMatch> freshMatches = new ArrayList<>();
                for (WatchlistMatch match : matches) {
                    if (!watchlistMatchPort.existsByItemAndArticle(match.itemId(), match.articleId())) {
                        freshMatches.add(match);
                    }
                }

                if (!freshMatches.isEmpty()) {
                    watchlistMatchPort.saveAll(freshMatches);
                    log.info("runDailyRegulatoryHarvest() | saved {} watchlist matches", freshMatches.size());
                }
            }

        } catch (Exception e) {
            log.error("runDailyRegulatoryHarvest() | regulatory harvest failed", e);
        }

        log.debug("runDailyRegulatoryHarvest() | return=void");
    }
}
