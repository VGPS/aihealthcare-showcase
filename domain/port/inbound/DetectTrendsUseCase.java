package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for the trend detection use case.
 *
 * <p>Provides operations to trigger trend analysis across the article corpus
 * and retrieve the most recent snapshot.  The implementation loads articles
 * from persistence, delegates to the trend detection algorithm, and stores
 * the resulting snapshot.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface DetectTrendsUseCase {

    /**
     * Runs trend detection across articles from the last 180 days.
     *
     * @return the newly generated trend snapshot
     */
    TrendSnapshot detectTrends();

    /**
     * Retrieves the most recently generated trend snapshot.
     *
     * @return the latest snapshot, or empty if none exists
     */
    Optional<TrendSnapshot> getLatestSnapshot();

    /**
     * Returns all stored trend snapshots, ordered by generation time descending.
     *
     * @return all snapshots; empty list if none exist
     */
    List<TrendSnapshot> getAllSnapshots();
}
