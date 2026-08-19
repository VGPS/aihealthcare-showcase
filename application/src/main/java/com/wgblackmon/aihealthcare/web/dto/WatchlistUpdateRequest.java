package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Request body for {@code PUT /api/market-digest/watchlist}.
 *
 * <p>Replaces the caller's entire watchlist with the provided ticker list.
 * Passing an empty list clears the watchlist.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record WatchlistUpdateRequest(List<String> tickers) {}
