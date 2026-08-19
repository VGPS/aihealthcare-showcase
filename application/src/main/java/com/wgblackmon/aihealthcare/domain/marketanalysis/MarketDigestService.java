package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Application-layer service orchestrating the daily AI-healthcare market digest pipeline.
 *
 * <p>Slice 1.2 implements the qualifying-bar filter ({@link #isMarketMoving}) and
 * the rank-ascending sort step. All other pipeline methods throw
 * {@link UnsupportedOperationException} until their respective slices are merged:
 * <ul>
 *   <li>{@link #generateDailyDigest} — completed in Slice 1.6 (scheduler/wiring)</li>
 * </ul>
 *
 * <p>Qualifying bar: an entry clears when its category is EARNINGS, REGULATORY, M_AND_A,
 * or MAJOR_PARTNERSHIP, or when it is FUNDING with a disclosed deal size strictly greater
 * than $50,000,000 (exact $50M does NOT qualify).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
public class MarketDigestService {

    private final MarketNewsResearchPort newsResearch;
    private final MarketDataPort marketData;
    private final ImpactClassifierPort impactClassifier;
    private final MarketDigestRepository repository;
    private final MarketDigestNotifier notifier;

    public MarketDigestService(
            MarketNewsResearchPort newsResearch,
            MarketDataPort marketData,
            ImpactClassifierPort impactClassifier,
            MarketDigestRepository repository,
            MarketDigestNotifier notifier) {
        this.newsResearch = newsResearch;
        this.marketData = marketData;
        this.impactClassifier = impactClassifier;
        this.repository = repository;
        this.notifier = notifier;
    }

    /**
     * Runs the full daily digest pipeline for the given date.
     * Completed in Slice 1.6.
     *
     * @param date the digest date (non-null)
     * @return the persisted digest
     */
    public MarketDigest generateDailyDigest(LocalDate date) {
        log.debug("generateDailyDigest() | date={}", date);
        // TODO Slice 1.6 — full pipeline wiring
        throw new UnsupportedOperationException("generateDailyDigest — implemented in Slice 1.6");
    }

    /**
     * Returns {@code true} when the entry clears the market-moving qualifying bar.
     *
     * <p>Qualifying categories: EARNINGS, REGULATORY, M_AND_A, MAJOR_PARTNERSHIP always qualify.
     * FUNDING qualifies only when {@code entry.dealSizeUsd()} is strictly greater than
     * $50,000,000 (exact $50M does NOT qualify). All other categories (OTHER, and FUNDING
     * at or below the threshold) return {@code false}.
     *
     * @param entry the classified digest entry to evaluate (non-null)
     * @return true if this entry should be included in the daily digest and notification
     */
    boolean isMarketMoving(MarketDigestEntry entry) {
        log.debug("isMarketMoving() | category={}, dealSizeUsd={}", entry.category(), entry.dealSizeUsd());
        boolean result;
        NewsCategory category = entry.category();
        if (category == NewsCategory.EARNINGS) {
            result = true;
        } else if (category == NewsCategory.REGULATORY) {
            result = true;
        } else if (category == NewsCategory.FUNDING) {
            result = entry.dealSizeUsd() > 50_000_000L;
        } else if (category == NewsCategory.M_AND_A) {
            result = true;
        } else if (category == NewsCategory.MAJOR_PARTNERSHIP) {
            result = true;
        } else {
            result = false;
        }
        log.debug("isMarketMoving() | return={}", result);
        return result;
    }

    /**
     * Filters the given entries to only qualifying ones, then sorts them ascending by rank
     * (rank 1 = highest market impact, returned first).
     *
     * @param entries entries to filter and sort (non-null)
     * @return new list containing only qualifying entries, sorted rank ascending
     */
    List<MarketDigestEntry> filterAndSort(List<MarketDigestEntry> entries) {
        log.debug("filterAndSort() | entries.size={}", entries.size());
        List<MarketDigestEntry> qualifying = new ArrayList<>();
        for (MarketDigestEntry entry : entries) {
            if (isMarketMoving(entry)) {
                qualifying.add(entry);
            }
        }
        qualifying.sort((a, b) -> Integer.compare(a.rank().value(), b.rank().value()));
        log.debug("filterAndSort() | return.size={}", qualifying.size());
        return qualifying;
    }
}
