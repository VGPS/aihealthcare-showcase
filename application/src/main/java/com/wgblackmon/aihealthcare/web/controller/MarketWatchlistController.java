package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.TickerWatchlistRepository;
import com.wgblackmon.aihealthcare.web.dto.WatchlistResponse;
import com.wgblackmon.aihealthcare.web.dto.WatchlistUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for the per-subscriber market ticker watchlist.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /api/market-digest/watchlist} — returns the caller's tracked tickers</li>
 *   <li>{@code PUT  /api/market-digest/watchlist} — atomically replaces the caller's watchlist</li>
 *   <li>{@code POST /api/market-digest/subscribe} — idempotent subscription trigger (returns
 *       the caller's current watchlist, initializing an empty one if this is their first call)</li>
 * </ul>
 *
 * <p>All endpoints require authentication. The caller is identified by their Spring
 * Security username (user email address).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@RestController
@RequestMapping("/api/market-digest")
public class MarketWatchlistController {

    private final TickerWatchlistRepository watchlistRepository;

    public MarketWatchlistController(TickerWatchlistRepository watchlistRepository) {
        log.debug("MarketWatchlistController() | watchlistRepository={}",
                watchlistRepository.getClass().getSimpleName());
        this.watchlistRepository = watchlistRepository;
        log.debug("MarketWatchlistController() | return=void");
    }

    /**
     * Returns the caller's current ticker watchlist.
     *
     * @param user the authenticated user (resolved from Spring Security context)
     * @return 200 with the watchlist response (empty tickers list if no watchlist configured)
     */
    @GetMapping("/watchlist")
    public ResponseEntity<WatchlistResponse> getWatchlist(
            @AuthenticationPrincipal UserDetails user) {
        String subscriberId = user.getUsername();
        log.debug("getWatchlist() | subscriberId={}", subscriberId);

        List<String> tickers = watchlistRepository.findWatchedTickers(subscriberId);
        WatchlistResponse response = new WatchlistResponse(subscriberId, tickers);

        log.debug("getWatchlist() | return=200, tickers.size={}", tickers.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Atomically replaces the caller's watchlist with the provided ticker list.
     *
     * <p>Passing an empty list clears the watchlist.
     *
     * @param user    the authenticated user (resolved from Spring Security context)
     * @param request request body containing the new ticker list
     * @return 200 with the updated watchlist
     */
    @PutMapping("/watchlist")
    public ResponseEntity<WatchlistResponse> replaceWatchlist(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody WatchlistUpdateRequest request) {
        String subscriberId = user.getUsername();
        log.debug("replaceWatchlist() | subscriberId={}, tickers.size={}",
                subscriberId, request.tickers() != null ? request.tickers().size() : 0);

        List<String> tickers = request.tickers() != null ? request.tickers() : new ArrayList<>();
        watchlistRepository.replaceWatchlist(subscriberId, tickers);

        List<String> updated = watchlistRepository.findWatchedTickers(subscriberId);
        WatchlistResponse response = new WatchlistResponse(subscriberId, updated);

        log.debug("replaceWatchlist() | return=200, tickers.size={}", updated.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Idempotent subscribe endpoint — registers the caller as a market digest subscriber.
     *
     * <p>Returns the caller's current watchlist (empty if this is their first subscription).
     * Repeated calls are safe and return the same data.
     *
     * @param user the authenticated user (resolved from Spring Security context)
     * @return 200 with the caller's current watchlist
     */
    @PostMapping("/subscribe")
    public ResponseEntity<WatchlistResponse> subscribe(
            @AuthenticationPrincipal UserDetails user) {
        String subscriberId = user.getUsername();
        log.debug("subscribe() | subscriberId={}", subscriberId);

        List<String> tickers = watchlistRepository.findWatchedTickers(subscriberId);
        WatchlistResponse response = new WatchlistResponse(subscriberId, tickers);

        log.debug("subscribe() | return=200, tickers.size={}", tickers.size());
        return ResponseEntity.ok(response);
    }
}
