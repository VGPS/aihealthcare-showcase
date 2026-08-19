package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import java.util.List;

/**
 * Outbound port for managing per-subscriber ticker watchlists.
 *
 * <p>Each subscriber maintains an ordered list of ticker symbols they want to
 * track in the market digest. The {@link #replaceWatchlist} method atomically
 * replaces the full watchlist (used by the PUT endpoint), while
 * {@link #addTicker} and {@link #removeTicker} provide incremental updates.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface TickerWatchlistRepository {

    /**
     * Returns the current ticker list for the given subscriber, in the order
     * they were added. Returns an empty list if the subscriber has no watchlist.
     *
     * @param subscriberId the subscriber's user email or user ID (non-null, non-blank)
     */
    List<String> findWatchedTickers(String subscriberId);

    /**
     * Adds a single ticker to the subscriber's watchlist (idempotent — no-op if
     * the ticker is already present).
     */
    void addTicker(String subscriberId, String ticker);

    /**
     * Removes a single ticker from the subscriber's watchlist (no-op if absent).
     */
    void removeTicker(String subscriberId, String ticker);

    /**
     * Atomically replaces the subscriber's entire watchlist with the given list.
     * Passing an empty list clears the watchlist.
     */
    void replaceWatchlist(String subscriberId, List<String> tickers);
}
