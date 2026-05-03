package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.MarketIntelligenceReport;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateMarketIntelligenceUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled driver that triggers monthly market intelligence report generation and
 * writes the resulting HTML to a dated file in the summaries directory.
 *
 * <p>The scheduler fires on the 1st of every month at 08:00 local time by default.
 * The cron expression is configurable via
 * {@code aihealthcare.market-intelligence.schedule} in {@code application.yml}.
 *
 * <p>The generated HTML is written to:
 * <pre>{@code
 *   <summariesDirectory>/healthcare_ai_market_intelligence_yyyy_MM_dd.html
 * }</pre>
 *
 * <p>File writing is intentionally located here (infrastructure) rather than in the
 * domain service, keeping {@link com.wgblackmon.aihealthcare.domain.service.MarketIntelligenceService}
 * free of I/O concerns and fully testable without a filesystem.
 *
 * <p>The public {@link #generateAndExport()} method is exposed so that
 * {@link com.wgblackmon.aihealthcare.web.controller.MarketIntelligenceController}
 * can trigger an on-demand refresh via {@code POST /api/v1/market-intelligence/refresh}
 * without duplicating file-writing logic.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
@Slf4j
@Component
public class MarketIntelligenceScheduler {

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private final GenerateMarketIntelligenceUseCase generateUseCase;
    private final String summariesDirectory;

    /**
     * Constructs the scheduler with its use case dependency and configured directories.
     *
     * @param generateUseCase    Use case that orchestrates prompt loading and AI generation.
     * @param summariesDirectory Path to the directory where dated HTML reports are written.
     *                           Resolved from {@code aihealthcare.notebooklm.summaries-directory};
     *                           defaults to {@code NotebookLMDirectory/summaries}.
     */
    public MarketIntelligenceScheduler(
            GenerateMarketIntelligenceUseCase generateUseCase,
            @Value("${aihealthcare.notebooklm.summaries-directory:NotebookLMDirectory/summaries}") String summariesDirectory) {
        log.debug("MarketIntelligenceScheduler() | generateUseCase={}, summariesDirectory={}",
                  generateUseCase.getClass().getSimpleName(), summariesDirectory);
        this.generateUseCase     = generateUseCase;
        this.summariesDirectory  = summariesDirectory;
        log.debug("MarketIntelligenceScheduler() | return=void");
    }

    /**
     * Monthly scheduled trigger — generates and exports the market intelligence report.
     *
     * <p>Fires on the 1st of every month at 08:00 local time by default.
     * The cron expression is configurable via
     * {@code aihealthcare.market-intelligence.schedule}.
     *
     * <p>Exceptions are caught and logged so the scheduler thread survives a failure.
     */
    @Scheduled(cron = "${aihealthcare.market-intelligence.schedule:0 0 8 1 * *}")
    public void runMonthlyReport() {
        log.debug("runMonthlyReport() | starting monthly market intelligence run");
        log.info("runMonthlyReport() | monthly market intelligence report triggered");
        try {
            Path written = generateAndExport();
            log.info("runMonthlyReport() | monthly report written to {}", written.toAbsolutePath());
        } catch (Exception ex) {
            log.error("runMonthlyReport() | failed to generate monthly report: {}", ex.getMessage(), ex);
        }
        log.debug("runMonthlyReport() | return=void");
    }

    /**
     * Generates the market intelligence report and writes it to a dated HTML file.
     *
     * <p>Called by both the monthly {@link #runMonthlyReport()} scheduler and the
     * on-demand {@link com.wgblackmon.aihealthcare.web.controller.MarketIntelligenceController}.
     * If a file already exists for today's date it is overwritten, so repeated
     * on-demand calls always reflect the latest AI response.
     *
     * @return The {@link Path} of the written HTML file.
     * @throws IOException if the summaries directory cannot be created or the file
     *                     cannot be written.
     */
    public Path generateAndExport() throws IOException {
        log.debug("generateAndExport() | starting generation");

        MarketIntelligenceReport report = generateUseCase.generate();
        log.info("generateAndExport() | report generated: date={}, htmlLength={}",
                 report.reportDate(), report.htmlContent().length());

        Path dir = Paths.get(summariesDirectory);
        if (!Files.exists(dir)) {
            log.info("generateAndExport() | creating summaries directory: {}", dir.toAbsolutePath());
            Files.createDirectories(dir);
        }

        String filename = "healthcare_ai_market_intelligence_"
                + report.reportDate().format(FILE_DATE_FORMAT) + ".html";
        Path file = dir.resolve(filename);

        Files.writeString(file, report.htmlContent(), StandardCharsets.UTF_8);
        log.info("generateAndExport() | wrote {} chars to {}", report.htmlContent().length(), file.toAbsolutePath());

        log.debug("generateAndExport() | return={}", file.toAbsolutePath());
        return file;
    }
}
