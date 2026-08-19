package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.GuidanceComparison;
import com.wgblackmon.aihealthcare.domain.marketanalysis.GuidanceQueryService;
import com.wgblackmon.aihealthcare.web.dto.GuidanceComparisonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * REST controller exposing earnings guidance history endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/market-digest/guidance/{ticker}?metric=} — most recent guidance
 *       comparison for the ticker+metric pair; 404 if no history exists</li>
 * </ul>
 *
 * <p>All endpoints require authentication ({@code /api/**} rule in SecurityConfig).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@RestController
@RequestMapping("/api/market-digest/guidance")
public class GuidanceController {

    private final GuidanceQueryService guidanceQueryService;

    public GuidanceController(GuidanceQueryService guidanceQueryService) {
        log.debug("GuidanceController() | guidanceQueryService={}", guidanceQueryService);
        this.guidanceQueryService = guidanceQueryService;
        log.debug("GuidanceController() | return=void");
    }

    /**
     * Returns the most recently stored guidance comparison for the given ticker.
     *
     * @param ticker the stock ticker symbol (e.g. "NVDA")
     * @param metric the metric name (e.g. "EPS", "REVENUE"); required
     * @return 200 with comparison body, or 404 if no history exists
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<GuidanceComparisonResponse> getGuidance(
            @PathVariable String ticker,
            @RequestParam String metric) {
        log.debug("getGuidance() | ticker={}, metric={}", ticker, metric);

        Optional<GuidanceComparison> guidance =
                guidanceQueryService.findLatestGuidance(ticker, metric);

        if (guidance.isEmpty()) {
            log.debug("getGuidance() | return=404");
            return ResponseEntity.notFound().build();
        }

        GuidanceComparisonResponse response = toResponse(guidance.get());
        log.debug("getGuidance() | return=200, ticker={}", response.tickerSymbol());
        return ResponseEntity.ok(response);
    }

    // ─── mapping helpers ────────────────────────────────────────────────────

    private GuidanceComparisonResponse toResponse(GuidanceComparison g) {
        return new GuidanceComparisonResponse(
                g.tickerSymbol(),
                g.priorGuidanceLow(),
                g.priorGuidanceHigh(),
                g.newGuidanceLow(),
                g.newGuidanceHigh(),
                g.metric()
        );
    }
}
