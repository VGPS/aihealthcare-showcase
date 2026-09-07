package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.Jurisdiction;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RegulatoryTracker;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.RegulatoryTrackerRepository;
import com.wgblackmon.aihealthcare.web.dto.RegulatoryTrackerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing regulatory tracker endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/market-digest/regulatory-tracker} — all tracked regulatory actions</li>
 *   <li>{@code GET /api/market-digest/regulatory-tracker/approaching?days=30} — trackers
 *       with comment deadlines within the specified window (default 30 days)</li>
 *   <li>{@code GET /api/market-digest/regulatory-tracker/{docketId}?jurisdiction=} — single
 *       tracker by docket ID and jurisdiction; 404 if not found</li>
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
@RequestMapping("/api/market-digest/regulatory-tracker")
public class RegulatoryTrackerController {

    private final RegulatoryTrackerRepository regulatoryTrackerRepository;

    public RegulatoryTrackerController(RegulatoryTrackerRepository regulatoryTrackerRepository) {
        log.debug("RegulatoryTrackerController() | regulatoryTrackerRepository={}", regulatoryTrackerRepository);
        this.regulatoryTrackerRepository = regulatoryTrackerRepository;
        log.debug("RegulatoryTrackerController() | return=void");
    }

    /**
     * Returns all currently tracked regulatory actions.
     *
     * @return 200 with list of tracker responses
     */
    @GetMapping
    public ResponseEntity<List<RegulatoryTrackerResponse>> getAll() {
        log.debug("getAll() |");

        List<RegulatoryTracker> trackers = regulatoryTrackerRepository.findAll();
        List<RegulatoryTrackerResponse> response = trackers.stream()
                .map(this::toResponse)
                .toList();

        log.debug("getAll() | return=200, count={}", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Returns trackers with comment deadlines approaching within the specified window.
     *
     * @param days number of days ahead to look for approaching deadlines (default 30)
     * @return 200 with list of tracker responses having approaching deadlines
     */
    @GetMapping("/approaching")
    public ResponseEntity<List<RegulatoryTrackerResponse>> getApproaching(
            @RequestParam(defaultValue = "30") int days) {
        log.debug("getApproaching() | days={}", days);

        LocalDate cutoff = LocalDate.now().plusDays(days);
        List<RegulatoryTracker> trackers = regulatoryTrackerRepository.findApproachingDeadlines(cutoff);
        List<RegulatoryTrackerResponse> response = trackers.stream()
                .map(this::toResponse)
                .toList();

        log.debug("getApproaching() | return=200, count={}", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a single tracker by docket ID and jurisdiction.
     *
     * @param docketId     the official docket reference number
     * @param jurisdiction the issuing regulatory body (required)
     * @return 200 with tracker body, or 404 if not found
     */
    @GetMapping("/{docketId}")
    public ResponseEntity<RegulatoryTrackerResponse> getByDocketId(
            @PathVariable String docketId,
            @RequestParam Jurisdiction jurisdiction) {
        log.debug("getByDocketId() | docketId={}, jurisdiction={}", docketId, jurisdiction);

        Optional<RegulatoryTracker> tracker =
                regulatoryTrackerRepository.findByDocketId(docketId, jurisdiction);

        if (tracker.isEmpty()) {
            log.debug("getByDocketId() | return=404");
            return ResponseEntity.notFound().build();
        }

        RegulatoryTrackerResponse response = toResponse(tracker.get());
        log.debug("getByDocketId() | return=200, docketId={}", response.docketId());
        return ResponseEntity.ok(response);
    }

    // --- mapping helpers ────────────────────────────────────────────────────

    private RegulatoryTrackerResponse toResponse(RegulatoryTracker t) {
        return new RegulatoryTrackerResponse(
                t.jurisdiction().name(),
                t.stage().name(),
                t.docketId(),
                t.title(),
                t.commentDeadline() != null ? t.commentDeadline().toString() : null,
                t.lastUpdatedAt().toString()
        );
    }
}
