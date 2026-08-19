package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/market-digest/watchlist}.
 *
 * <p>Returns the subscriber's current tracked ticker list in the order
 * the tickers were added.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record WatchlistResponse(String subscriberId, List<String> tickers) {}
