package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ResearchRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchRunPort;
import com.wgblackmon.aihealthcare.web.dto.ResearchRunResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST controller that exposes persisted research run history.
 *
 * <p>Provides two read-only endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/research/runs} — returns all runs, most recent first.</li>
 *   <li>{@code GET /api/v1/research/runs/{runId}} — returns a single run by ID,
 *       or HTTP 404 if not found.</li>
 * </ul>
 *
 * <p>Reads directly from {@link ResearchRunPort} — no application service layer is
 * needed for pure query operations that involve no business logic.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/research/runs")
public class ResearchRunController {

    private final ResearchRunPort researchRunPort;

    /**
     * Constructs the controller with its outbound port dependency.
     *
     * @param researchRunPort Port for querying persisted research run records.
     */
    public ResearchRunController(ResearchRunPort researchRunPort) {
        log.debug("ResearchRunController() | researchRunPort={}",
                  researchRunPort.getClass().getSimpleName());
        this.researchRunPort = researchRunPort;
        log.debug("ResearchRunController() | return=void");
    }

    /**
     * Returns all persisted research runs, most recent first.
     *
     * @return HTTP 200 with a list of {@link ResearchRunResponse} DTOs.
     */
    @GetMapping
    public ResponseEntity<List<ResearchRunResponse>> listRuns() {
        log.debug("listRuns() |");

        List<ResearchRun> runs = researchRunPort.findAll();
        List<ResearchRunResponse> result = new ArrayList<>();
        for (ResearchRun run : runs) {
            result.add(toDto(run));
        }

        log.info("listRuns() | returning {} research runs", result.size());
        log.debug("listRuns() | return={} runs", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns a single research run by its ID.
     *
     * @param runId the UUID assigned at conduct time
     * @return HTTP 200 with the {@link ResearchRunResponse}, or HTTP 404 if not found.
     */
    @GetMapping("/{runId}")
    public ResponseEntity<ResearchRunResponse> getRun(@PathVariable String runId) {
        log.debug("getRun() | runId={}", runId);

        Optional<ResearchRun> run = researchRunPort.findByRunId(runId);
        if (run.isEmpty()) {
            log.warn("getRun() | run not found: runId={}", runId);
            log.debug("getRun() | return=404");
            return ResponseEntity.notFound().build();
        }

        ResearchRunResponse result = toDto(run.get());
        log.debug("getRun() | return={}", result.runId());
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ResearchRunResponse toDto(ResearchRun run) {
        log.debug("toDto() | runId={}", run.runId());
        ResearchRunResponse result = new ResearchRunResponse(
                run.runId(),
                run.query(),
                run.mode(),
                run.citationCount(),
                run.researchedAt() != null ? run.researchedAt().toString() : null);
        log.debug("toDto() | return={}", result.runId());
        return result;
    }
}
