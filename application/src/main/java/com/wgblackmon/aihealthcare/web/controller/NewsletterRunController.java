package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.web.dto.RunDetailResponse;
import com.wgblackmon.aihealthcare.web.dto.RunSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller exposing the newsletter run archive endpoints.
 *
 * <p>Two endpoints serve the subscriber archive use case:
 * <ol>
 *   <li>{@code GET /api/v1/runs} — returns a compact summary list of all
 *       historical runs (no HTML content) suitable for an archive index page.</li>
 *   <li>{@code GET /api/v1/runs/{runId}} — returns the full run detail
 *       including {@code htmlContent} (for a styled {@code <div>} rendering)
 *       and {@code plainTextContent} (for a {@code <textarea>}).</li>
 * </ol>
 *
 * <p>Domain exceptions (e.g. {@code RunNotFoundException}) are mapped to HTTP
 * status codes by {@link GlobalExceptionHandler} — this controller contains no
 * try/catch blocks.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class NewsletterRunController {

    private final NewsletterRunPort newsletterRunPort;

    public NewsletterRunController(NewsletterRunPort newsletterRunPort) {
        log.debug("NewsletterRunController() | newsletterRunPort={}",
                  newsletterRunPort.getClass().getSimpleName());
        this.newsletterRunPort = newsletterRunPort;
    }

    /**
     * Returns a summary list of all persisted newsletter runs.
     *
     * @return 200 OK with list of {@link RunSummaryResponse} (no HTML content)
     */
    @GetMapping("/runs")
    public ResponseEntity<List<RunSummaryResponse>> listRuns() {
        log.debug("listRuns() |");

        List<NewsletterRun> runs = newsletterRunPort.findAll();
        List<RunSummaryResponse> summaries = new ArrayList<>();

        for (NewsletterRun run : runs) {
            summaries.add(new RunSummaryResponse(
                    run.runId(),
                    run.title(),
                    run.weekOf(),
                    run.status().name(),
                    run.generatedAt()
            ));
        }

        log.debug("listRuns() | return={} runs", summaries.size());
        return ResponseEntity.ok(summaries);
    }

    /**
     * Returns the full detail of a single newsletter run including both rendered
     * content formats.
     *
     * @param runId path variable identifying the run
     * @return 200 OK with {@link RunDetailResponse}, or 404 via
     *         {@link GlobalExceptionHandler} if not found
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunDetailResponse> getNewsletterRun(@PathVariable String runId) {
        log.debug("getNewsletterRun() | runId={}", runId);

        NewsletterRun run = newsletterRunPort.findByRunId(runId);

        RunDetailResponse response = new RunDetailResponse(
                run.runId(),
                run.title(),
                run.weekOf(),
                run.htmlContent(),
                run.plainTextContent(),
                run.status().name(),
                run.generatedAt()
        );

        log.debug("getNewsletterRun() | return={}", response.runId());
        return ResponseEntity.ok(response);
    }
}
