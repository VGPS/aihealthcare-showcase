package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

/**
 * Admin controller for rendering newsletter runs as a browsable Thymeleaf
 * page — no email client required.
 *
 * <p>{@code GET /monitoring/newsletter/preview} — latest AI newsletter run.
 * {@code GET /monitoring/newsletter/preview/{runId}} — specific run by ID.
 *
 * <p>The page embeds the exact HTML sent to subscribers (unmodified) in an
 * isolated iframe, and offers a one-click "Copy for Social Media" button
 * that places both the HTML and plain-text versions on the clipboard
 * simultaneously so the formatting survives a paste into Medium, X, or
 * LinkedIn's post composer.
 *
 * @author  Bill Blackmon
 * @since   2026-08-15
 * @updated 2026-08-17
 */
@Slf4j
@Controller
@RequestMapping("/monitoring/newsletter")
public class MonitoringNewsletterController {

    private final NewsletterRunPort newsletterRunPort;

    public MonitoringNewsletterController(NewsletterRunPort newsletterRunPort) {
        log.debug("MonitoringNewsletterController() | newsletterRunPort={}",
                newsletterRunPort.getClass().getSimpleName());
        this.newsletterRunPort = newsletterRunPort;
    }

    @GetMapping("/preview")
    public String previewLatest(Model model) {
        log.debug("previewLatest() | (no args)");
        Optional<NewsletterRun> run = newsletterRunPort.findLatest();
        if (run.isEmpty()) {
            log.debug("previewLatest() | return=newsletter-preview (no runs yet)");
            model.addAttribute("run", null);
            return "newsletter-preview";
        }
        populateModel(model, run.get());
        log.debug("previewLatest() | return=newsletter-preview runId={}", run.get().runId());
        return "newsletter-preview";
    }

    @GetMapping("/preview/{runId}")
    public String previewById(@PathVariable String runId, Model model) {
        log.debug("previewById() | runId={}", runId);
        NewsletterRun run = newsletterRunPort.findByRunId(runId);
        populateModel(model, run);
        log.debug("previewById() | return=newsletter-preview");
        return "newsletter-preview";
    }

    private void populateModel(Model model, NewsletterRun run) {
        log.debug("populateModel() | runId={}", run.runId());
        model.addAttribute("run", run);
        model.addAttribute("runId", run.runId());
        model.addAttribute("title", run.title());
        model.addAttribute("weekOf", run.weekOf());
        model.addAttribute("status", run.status());
        model.addAttribute("htmlContent", run.htmlContent());
        model.addAttribute("plainTextContent", run.plainTextContent());
        log.debug("populateModel() | return=void");
    }
}
