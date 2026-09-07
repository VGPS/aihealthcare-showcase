package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port for the daily market digest use case.
 *
 * <p>Drives the full pipeline: news research → LLM classification →
 * market data enrichment → qualifying-bar filter → ranking → persistence → notification.
 * Also provides read-access to previously generated digests.
 *
 * <p>Implemented by {@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
public interface ProduceMarketDigestUseCase {

    /**
     * Runs the daily digest pipeline for the given date. Idempotent — re-running
     * on the same date returns the existing digest without repeating the pipeline.
     *
     * @param date the digest date (non-null)
     * @return the generated (or pre-existing) digest
     */
    MarketDigest generateDailyDigest(LocalDate date);

    /**
     * Retrieves the most recently generated digest.
     *
     * @return the latest digest, or empty if none exist
     */
    Optional<MarketDigest> findLatest();

    /**
     * Retrieves the digest for a specific business date.
     *
     * @param date the business date
     * @return the digest if one exists for that date
     */
    Optional<MarketDigest> findByDate(LocalDate date);

    /**
     * Retrieves all persisted digests ordered newest-first.
     *
     * @return all digests; empty list if none exist
     */
    List<MarketDigest> findAll();

    /**
     * Retrieves digests whose date falls within the given range (inclusive),
     * ordered newest-first.
     *
     * @param from start date (inclusive)
     * @param to   end date (inclusive)
     * @return matching digests; empty list if none found
     */
    List<MarketDigest> findByDateRange(LocalDate from, LocalDate to);
}
