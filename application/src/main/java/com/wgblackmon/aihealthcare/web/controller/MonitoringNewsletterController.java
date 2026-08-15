package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Admin REST controller for rendering newsletter runs as HTML directly in
 * the browser — no email client required.
 *
 * <p>{@code GET /monitoring/newsletter/preview} — latest AI newsletter run.
 * {@code GET /monitoring/newsletter/preview/{runId}} — specific run by ID.
 *
 * @author  Bill Blackmon
 * @since   2026-08-15
 * @updated 2026-08-15
 */
@Slf4j
@RestController
@RequestMapping("/monitoring/newsletter")
public class MonitoringNewsletterController {

    private final NewsletterRunPort newsletterRunPort;

    public MonitoringNewsletterController(NewsletterRunPort newsletterRunPort) {
        log.debug("MonitoringNewsletterController() | newsletterRunPort={}",
                newsletterRunPort.getClass().getSimpleName());
        this.newsletterRunPort = newsletterRunPort;
    }

    @GetMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewLatest() {
        log.debug("previewLatest() | (no args)");
        Optional<NewsletterRun> run = newsletterRunPort.findLatest();
        if (run.isEmpty()) {
            log.debug("previewLatest() | return=204 (no runs yet)");
            return ResponseEntity.noContent().build();
        }
        log.debug("previewLatest() | return=200 runId={}", run.get().runId());
        return ResponseEntity.ok(run.get().htmlContent());
    }

    @GetMapping(value = "/preview/{runId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewById(@PathVariable String runId) {
        log.debug("previewById() | runId={}", runId);
        NewsletterRun run = newsletterRunPort.findByRunId(runId);
        log.debug("previewById() | return=200");
        return ResponseEntity.ok(run.htmlContent());
    }
}
