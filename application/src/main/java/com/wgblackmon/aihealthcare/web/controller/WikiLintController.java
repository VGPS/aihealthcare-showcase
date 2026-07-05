package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.port.outbound.LintReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.WikiLintService;
import com.wgblackmon.aihealthcare.web.dto.LintReportResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for wiki lint operations.
 *
 * <p>Provides a manual trigger at {@code POST /monitoring/wiki/lint} and
 * a latest-report endpoint at {@code GET /monitoring/wiki/lint/latest}.
 * The path {@code /monitoring/**} is already {@code permitAll()} in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
@Slf4j
@RestController
public class WikiLintController {

    private final WikiLintService lintService;
    private final WikiQueryPort wikiQueryPort;
    private final LintReportPort lintReportPort;
    private final int stalenessThresholdDays;

    public WikiLintController(WikiLintService lintService,
                               WikiQueryPort wikiQueryPort,
                               LintReportPort lintReportPort,
                               @Value("${aihealthcare.wiki.staleness-threshold-days:30}") int stalenessThresholdDays) {
        log.debug("WikiLintController() | lintService={}, wikiQueryPort={}, lintReportPort={}, stalenessThresholdDays={}",
                lintService.getClass().getSimpleName(),
                wikiQueryPort.getClass().getSimpleName(),
                lintReportPort.getClass().getSimpleName(),
                stalenessThresholdDays);
        this.lintService = lintService;
        this.wikiQueryPort = wikiQueryPort;
        this.lintReportPort = lintReportPort;
        this.stalenessThresholdDays = stalenessThresholdDays;
    }

    /**
     * Triggers a manual wiki lint run.
     *
     * @return lint report with all detected issues
     */
    @PostMapping("/monitoring/wiki/lint")
    public ResponseEntity<LintReportResponse> triggerLint() {
        log.debug("triggerLint() | (no args)");

        List<WikiPage> allPages = wikiQueryPort.findRelevantPages("", 10000);
        log.info("triggerLint() | loaded {} wiki pages for linting", allPages.size());

        LintReport report = lintService.lint(allPages, stalenessThresholdDays);
        lintReportPort.save(report);

        LintReportResponse response = LintReportResponse.from(report);
        log.debug("triggerLint() | return={}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the most recent lint report.
     *
     * @return the latest lint report, or 404 if no reports exist
     */
    @GetMapping("/monitoring/wiki/lint/latest")
    public ResponseEntity<LintReportResponse> getLatest() {
        log.debug("getLatest() | (no args)");

        LintReport latest = lintReportPort.findLatest();
        if (latest == null) {
            log.debug("getLatest() | return=404 (no reports)");
            return ResponseEntity.notFound().build();
        }

        LintReportResponse response = LintReportResponse.from(latest);
        log.debug("getLatest() | return={}", response);
        return ResponseEntity.ok(response);
    }
}
