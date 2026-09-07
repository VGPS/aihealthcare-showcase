package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PeerGroup;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PrivateFundingRound;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PrivateFundingPort;
import com.wgblackmon.aihealthcare.web.dto.PrivateFundingRoundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * REST controller exposing private funding round endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/market-digest/funding?days=90&peerGroup=} — recent funding
 *       rounds within the specified window (default 90 days), optionally filtered
 *       by {@link PeerGroup} enum name</li>
 * </ul>
 *
 * <p>All endpoints require authentication ({@code /api/**} rule in SecurityConfig).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
@Slf4j
@RestController
@RequestMapping("/api/market-digest/funding")
public class PrivateFundingController {

    private final PrivateFundingPort privateFundingPort;

    public PrivateFundingController(PrivateFundingPort privateFundingPort) {
        log.debug("PrivateFundingController() | privateFundingPort={}", privateFundingPort);
        this.privateFundingPort = privateFundingPort;
        log.debug("PrivateFundingController() | return=void");
    }

    /**
     * Returns recent private funding rounds, optionally filtered by peer group.
     *
     * @param days      number of days to look back for funding rounds (default 90)
     * @param peerGroup optional peer group filter; must match a {@link PeerGroup} enum name
     * @return 200 with list of funding round responses
     */
    @GetMapping
    public ResponseEntity<List<PrivateFundingRoundResponse>> getRecentFunding(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(required = false) String peerGroup) {
        log.debug("getRecentFunding() | days={}, peerGroup={}", days, peerGroup);

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        PeerGroup filter = peerGroup != null ? PeerGroup.valueOf(peerGroup) : null;

        List<PrivateFundingRound> rounds = privateFundingPort.findRecentRounds(since, filter);
        List<PrivateFundingRoundResponse> response = rounds.stream()
                .map(this::toResponse)
                .toList();

        log.debug("getRecentFunding() | return=200, count={}", response.size());
        return ResponseEntity.ok(response);
    }

    // --- mapping helpers ────────────────────────────────────────────────────

    private PrivateFundingRoundResponse toResponse(PrivateFundingRound r) {
        return new PrivateFundingRoundResponse(
                r.companyName(),
                r.roundStage(),
                r.amountUsd(),
                r.leadInvestors(),
                r.announcedAt().toString()
        );
    }
}
