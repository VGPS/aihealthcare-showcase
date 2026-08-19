package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AnalystRatingChange;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for persisting and retrieving analyst rating changes.
 *
 * <p>Rating changes are inserted as-is (no dedup): the same ticker may
 * receive multiple rating actions from different firms on the same day.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface AnalystRatingPort {

    /**
     * Persists a rating change.
     *
     * @param change the rating change to save (non-null)
     */
    void save(AnalystRatingChange change);

    /**
     * Returns all rating changes for the given ticker on or after {@code since},
     * ordered by {@code changedAt} descending (most recent first).
     *
     * @param tickerSymbol the stock ticker to query (non-null, non-blank)
     * @param since        lower bound for {@code changedAt} (inclusive, non-null)
     */
    List<AnalystRatingChange> findRecentChanges(String tickerSymbol, Instant since);
}
