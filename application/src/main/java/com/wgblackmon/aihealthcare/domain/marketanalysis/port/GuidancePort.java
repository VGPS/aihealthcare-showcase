package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.GuidanceComparison;

import java.util.Optional;

/**
 * Outbound port for persisting and retrieving earnings guidance history.
 *
 * <p>Implementations store each {@link GuidanceComparison} as it is extracted from
 * a processed EARNINGS news item, and expose the most recent comparison for a
 * ticker+metric pair so the pipeline can assess beat/miss against prior guidance.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface GuidancePort {

    /**
     * Returns the most recently recorded guidance comparison for the given
     * ticker symbol and metric, or empty if no history exists.
     */
    Optional<GuidanceComparison> getPriorGuidance(String tickerSymbol, String metric);

    /**
     * Persists a new guidance comparison, appending to the ticker's history.
     * Duplicate entries (same ticker, metric, and effective date) are allowed —
     * callers are responsible for deduplicating when needed.
     */
    void recordGuidance(GuidanceComparison comparison);
}
