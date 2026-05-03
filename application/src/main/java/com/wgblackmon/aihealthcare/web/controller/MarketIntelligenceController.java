package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.config.MarketIntelligenceScheduler;
import com.wgblackmon.aihealthcare.web.dto.MarketIntelligenceRefreshResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;

/**
 * REST controller that exposes an on-demand trigger for market intelligence report generation.
 *
 * <p>Delegates all generation and file-writing logic to
 * {@link MarketIntelligenceScheduler#generateAndExport()}, which is shared with the
 * monthly scheduled trigger to avoid duplicating I/O concerns.
 *
 * <h2>Endpoint</h2>
 * <pre>
 * POST /api/v1/market-intelligence/refresh
 * </pre>
 * Returns a {@link MarketIntelligenceRefreshResponse} with the file path, report date,
 * and length of the generated HTML document.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/market-intelligence")
public class MarketIntelligenceController {

    private final MarketIntelligenceScheduler scheduler;

    /**
     * Constructs the controller with its scheduler dependency.
     *
     * @param scheduler Scheduler that owns the generate-and-export logic.
     */
    public MarketIntelligenceController(MarketIntelligenceScheduler scheduler) {
        log.debug("MarketIntelligenceController() | scheduler={}", scheduler.getClass().getSimpleName());
        this.scheduler = scheduler;
        log.debug("MarketIntelligenceController() | return=void");
    }

    /**
     * Triggers an on-demand market intelligence report generation.
     *
     * <p>Calls the AI with the {@code MARKET_INTELLIGENCE} prompt, writes the HTML
     * result to {@code NotebookLMDirectory/summaries/healthcare_ai_market_intelligence_yyyy_MM_dd.html},
     * and returns metadata about the written file.  If a file already exists for today's
     * date it is overwritten, so this endpoint always returns the freshest AI response.
     *
     * @return {@code 200 OK} with a {@link MarketIntelligenceRefreshResponse}, or
     *         {@code 500 Internal Server Error} if generation or file writing fails.
     */
    @PostMapping("/refresh")
    public ResponseEntity<MarketIntelligenceRefreshResponse> refresh() {
        log.debug("refresh() | on-demand market intelligence refresh triggered");
        log.info("refresh() | POST /api/v1/market-intelligence/refresh received");

        try {
            Path file = scheduler.generateAndExport();

            // Extract report date from filename: healthcare_ai_market_intelligence_yyyy_MM_dd.html
            String filename = file.getFileName().toString();
            String datePart = filename
                    .replace("healthcare_ai_market_intelligence_", "")
                    .replace(".html", "")
                    .replace("_", "-");

            long htmlLength = java.nio.file.Files.size(file);

            MarketIntelligenceRefreshResponse body = new MarketIntelligenceRefreshResponse(
                    file.toAbsolutePath().toString(),
                    datePart,
                    (int) htmlLength
            );

            log.info("refresh() | report written: path={}, date={}, size={} bytes",
                     body.filePath(), body.reportDate(), body.htmlLength());
            log.debug("refresh() | return={}", body);
            return ResponseEntity.ok(body);

        } catch (IllegalStateException ex) {
            log.error("refresh() | prompt not configured: {}", ex.getMessage());
            log.debug("refresh() | return=500");
            return ResponseEntity.internalServerError().build();
        } catch (IOException ex) {
            log.error("refresh() | file write failed: {}", ex.getMessage(), ex);
            log.debug("refresh() | return=500");
            return ResponseEntity.internalServerError().build();
        }
    }
}
