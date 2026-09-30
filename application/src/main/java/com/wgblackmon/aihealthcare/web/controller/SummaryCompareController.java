package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.summary.SummaryCompareReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

/**
 * Admin-only REST endpoints that trigger the anti-slop summary pipeline reports.
 *
 * <p>Both endpoints are synchronous for simplicity — the Phase 1 lint scan is fast
 * (no LLM calls) and Phase 2 is bounded by {@code maxTopics}.  The response body
 * includes the output file path so the caller knows where to find the report.
 *
 * <p>Both endpoints require {@code ROLE_ADMIN}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
@Slf4j
@RestController
@RequestMapping("/admin/summary")
@PreAuthorize("hasRole('ADMIN')")
public class SummaryCompareController {

    private final SummaryCompareReportService reportService;

    public SummaryCompareController(SummaryCompareReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Phase 1 — lint all existing topic summaries and write a baseline markdown report.
     * No LLM calls; fast.
     *
     * @return 200 with the output file path, or 500 on IO failure
     */
    @PostMapping("/lint-report")
    public ResponseEntity<Map<String, String>> lintReport() {
        log.debug("lintReport() | triggered");
        try {
            Path output = reportService.runLintBaseline();
            log.debug("lintReport() | return path={}", output);
            return ResponseEntity.ok(Map.of(
                    "status", "complete",
                    "file",   output.toAbsolutePath().toString()
            ));
        } catch (Exception e) {
            log.error("lintReport() | failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Phase 2 — run the full extraction + writer pipeline and write a before/after report.
     * Makes LLM calls; one extraction + one write call per topic processed.
     *
     * @param maxTopics number of topic summaries to process (default 5)
     * @return 200 with the output file path, or 500 on failure
     */
    @PostMapping("/before-after")
    public ResponseEntity<Map<String, String>> beforeAfter(
            @RequestParam(defaultValue = "5") int maxTopics) {
        log.debug("beforeAfter() | maxTopics={}", maxTopics);
        try {
            Path output = reportService.runBeforeAfter(maxTopics);
            log.debug("beforeAfter() | return path={}", output);
            return ResponseEntity.ok(Map.of(
                    "status",       "complete",
                    "file",         output.toAbsolutePath().toString(),
                    "topicsRun",    String.valueOf(maxTopics)
            ));
        } catch (Exception e) {
            log.error("beforeAfter() | failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
