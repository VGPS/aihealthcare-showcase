package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application-layer service orchestrating the daily AI-healthcare market digest pipeline.
 *
 * <p>The full daily digest pipeline (implemented in Slice 1.6):
 * <ol>
 *   <li>Skip if a digest for {@code date} already exists in the repository.</li>
 *   <li>Research: fetch raw news from the 24h window ending at midnight on {@code date}.</li>
 *   <li>Wrap each item in a preliminary {@link MarketDigestEntry} (SPECULATIVE/rank-5/empty assessments).</li>
 *   <li>Classify: enrich assessments, fact classification, and rank via {@link ImpactClassifierPort}.</li>
 *   <li>Filter: discard non-qualifying entries; sort qualifying entries rank-ascending.</li>
 *   <li>Persist the resulting {@link MarketDigest} via {@link MarketDigestRepository}.</li>
 *   <li>Notify via {@link MarketDigestNotifier} when ≥1 entry qualifies (notifier is nullable).</li>
 * </ol>
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
     *
     * <p>Idempotent: if a digest already exists for {@code date} it is returned
     * unchanged — the pipeline is not re-executed.
     *
     * @param date the digest date (non-null)
     * @return the persisted (or pre-existing) digest
     */
    public MarketDigest generateDailyDigest(LocalDate date) {
        log.debug("generateDailyDigest() | date={}", date);

        Optional<MarketDigest> existing = repository.findByDate(date);
        if (existing.isPresent()) {
            log.info("generateDailyDigest() | digest already exists for {} — skipping pipeline", date);
            log.debug("generateDailyDigest() | return=existing");
            return existing.get();
        }

        Instant since = date.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<MarketNewsItem> newsItems = newsResearch.findRecentAiHealthcareNews(since);
        log.info("generateDailyDigest() | research returned {} raw items for {}", newsItems.size(), date);

        if (newsItems.isEmpty()) {
            MarketDigest emptyDigest = MarketDigest.empty(date);
            repository.save(emptyDigest);
            log.info("generateDailyDigest() | saved empty digest for {}", date);
            log.debug("generateDailyDigest() | return=emptyDigest");
            return emptyDigest;
        }

        List<MarketDigestEntry> preliminary = new ArrayList<>();
        for (MarketNewsItem item : newsItems) {
            preliminary.add(new MarketDigestEntry(
                    item,
                    new ArrayList<>(),
                    FactClassification.SPECULATIVE,
                    new MarketImpactRank(5),
                    new ArrayList<>()
            ));
        }

        List<MarketDigestEntry> classified = impactClassifier.classify(preliminary);
        List<MarketDigestEntry> qualified  = filterAndSort(classified);

        log.info("generateDailyDigest() | {} of {} items cleared qualifying bar for {}",
                qualified.size(), classified.size(), date);

        MarketDigest digest = new MarketDigest(date, qualified, Instant.now());
        repository.save(digest);
        log.info("generateDailyDigest() | digest saved for {}", date);

        if (!qualified.isEmpty() && notifier != null) {
            try {
                notifier.notify(digest);
                log.info("generateDailyDigest() | notifier invoked for {} qualifying entries", qualified.size());
            } catch (Exception e) {
                log.warn("generateDailyDigest() | notifier failed (non-fatal): {}", e.getMessage());
            }
        }

        log.debug("generateDailyDigest() | return={}", digest);
        return digest;
    }

    /**
     * Returns the most recently generated digest, or empty if none has been persisted yet.
     *
     * @return the latest digest wrapped in Optional, or empty
     */
    public Optional<MarketDigest> findLatest() {
        log.debug("findLatest()");
        Optional<MarketDigest> result = repository.findLatest();
        log.debug("findLatest() | return=present:{}", result.isPresent());
        return result;
    }

    /**
     * Returns the digest for the given date, or empty if none exists.
     *
     * @param date the digest date to query (non-null)
     * @return the digest wrapped in Optional, or empty
     */
    public Optional<MarketDigest> findByDate(LocalDate date) {
        log.debug("findByDate() | date={}", date);
        Optional<MarketDigest> result = repository.findByDate(date);
        log.debug("findByDate() | return=present:{}", result.isPresent());
        return result;
    }

    /**
     * Returns all persisted digests ordered newest-first.
     *
     * @return list of all digests; empty list if none exist
     */
    public List<MarketDigest> findAll() {
        log.debug("findAll()");
        List<MarketDigest> result = repository.findAll();
        log.debug("findAll() | return.size={}", result.size());
        return result;
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
