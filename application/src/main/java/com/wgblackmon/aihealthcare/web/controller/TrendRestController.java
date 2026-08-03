package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing trend detection endpoints.
 *
 * <p>{@code GET /api/v1/trends/latest} returns the most recent trend snapshot.
 * {@code POST /api/v1/trends/detect} triggers on-demand trend detection.
 * {@code GET /api/v1/trends/history} returns all stored snapshots.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-22
 * @updated 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/trends")
public class TrendRestController {

    private final DetectTrendsUseCase detectTrendsUseCase;

    public TrendRestController(DetectTrendsUseCase detectTrendsUseCase) {
        log.debug("TrendRestController() | detectTrendsUseCase={}", detectTrendsUseCase);
        this.detectTrendsUseCase = detectTrendsUseCase;
    }

    /**
     * Returns the most recently generated trend snapshot.
     *
     * @return 200 with snapshot, or 204 if none exists
     */
    @GetMapping("/latest")
    public ResponseEntity<TrendSnapshot> getLatest() {
        log.debug("getLatest()");

        Optional<TrendSnapshot> snapshot = detectTrendsUseCase.getLatestSnapshot();

        if (snapshot.isPresent()) {
            log.debug("getLatest() | return=200");
            return ResponseEntity.ok(snapshot.get());
        }

        log.debug("getLatest() | return=204");
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns all stored trend snapshots, ordered by generation time descending.
     *
     * @return 200 with list of snapshots (may be empty)
     */
    @GetMapping("/history")
    public ResponseEntity<List<TrendSnapshot>> getHistory() {
        log.debug("getHistory()");

        List<TrendSnapshot> snapshots = detectTrendsUseCase.getAllSnapshots();

        log.debug("getHistory() | return=200, count={}", snapshots.size());
        return ResponseEntity.ok(snapshots);
    }

    /**
     * Triggers on-demand trend detection and returns the new snapshot.
     *
     * @return 200 with the newly generated snapshot
     */
    @PostMapping("/detect")
    public ResponseEntity<TrendSnapshot> triggerDetection() {
        log.debug("triggerDetection()");

        TrendSnapshot snapshot = detectTrendsUseCase.detectTrends();

        log.debug("triggerDetection() | return=200, totalKeywords={}", snapshot.totalKeywords());
        return ResponseEntity.ok(snapshot);
    }
}
