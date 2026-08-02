package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf controller for the Newsletter Preview and Edit UI.
 *
 * <p>Provides a run list page at {@code GET /newsletter/runs} and a rich
 * HTML editor page at {@code GET /newsletter/runs/{runId}/edit} powered by
 * TinyMCE (served via WebJars).  Users can preview, edit, save, and send
 * newsletter drafts before delivery to subscribers.
 *
 * <p>Editing is only available for runs in {@link NewsletterRunStatus#DRAFT}
 * status.  Saving regenerates the plain-text version from the edited HTML
 * using Jsoup for clean tag stripping.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-18
 * @updated 2026-08-02
 */
@Slf4j
@Controller
@RequestMapping("/newsletter/runs")
public class NewsletterPreviewController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final NewsletterRunPort newsletterRunPort;
    private final DeliverNewsletterUseCase deliverUseCase;

    public NewsletterPreviewController(NewsletterRunPort newsletterRunPort,
                                       DeliverNewsletterUseCase deliverUseCase) {
        log.debug("NewsletterPreviewController() | newsletterRunPort={}, deliverUseCase={}",
                  newsletterRunPort.getClass().getSimpleName(),
                  deliverUseCase.getClass().getSimpleName());
        this.newsletterRunPort = newsletterRunPort;
        this.deliverUseCase    = deliverUseCase;
    }

    /**
     * Lists all newsletter runs with status badges and edit links for DRAFT runs.
     *
     * @param model Thymeleaf model.
     * @return view name {@code "newsletter-runs"}.
     */
    @GetMapping
    public String listRuns(
            @RequestParam(required = false, defaultValue = "generated_desc") String sort,
            Model model) {
        log.debug("listRuns() | sort={}", sort);

        List<NewsletterRun> runs = newsletterRunPort.findAll();
        runs = sortRuns(runs, sort);

        Map<String, String> runTimestamps = new HashMap<>();
        for (NewsletterRun run : runs) {
            if (run.generatedAt() != null) {
                runTimestamps.put(run.runId(), DISPLAY_FMT.format(run.generatedAt()) + " UTC");
            }
        }

        model.addAttribute("runs", runs);
        model.addAttribute("runTimestamps", runTimestamps);
        model.addAttribute("sort", sort);

        log.debug("listRuns() | return=newsletter-runs (runCount={})", runs.size());
        return "newsletter-runs";
    }

    /**
     * Sorts the newsletter run list by the specified column.
     */
    private List<NewsletterRun> sortRuns(List<NewsletterRun> runs, String sort) {
        log.debug("sortRuns() | sort={}, size={}", sort, runs.size());
        if (runs.isEmpty()) {
            log.debug("sortRuns() | return=empty list");
            return runs;
        }

        boolean descending = sort != null && sort.endsWith("_desc");
        String column = descending ? sort.substring(0, sort.length() - 5) : sort;

        Comparator<NewsletterRun> comparator;
        if ("title".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    r -> r.title() != null ? r.title() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("weekof".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    r -> r.weekOf() != null ? r.weekOf() : LocalDate.EPOCH);
        } else if ("status".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(r -> r.status().name());
        } else {
            // Default: generated date
            comparator = Comparator.comparing(
                    r -> r.generatedAt() != null ? r.generatedAt() : Instant.EPOCH);
        }

        if (descending) {
            comparator = comparator.reversed();
        }

        List<NewsletterRun> sorted = new ArrayList<>(runs);
        sorted.sort(comparator);
        log.debug("sortRuns() | return=sorted list, size={}", sorted.size());
        return sorted;
    }

    /**
     * Loads a newsletter run into the TinyMCE editor for preview and editing.
     *
     * @param runId the run identifier.
     * @param saved optional flag indicating a successful save (flash message).
     * @param model Thymeleaf model.
     * @return view name {@code "newsletter-edit"}.
     */
    @GetMapping("/{runId}/edit")
    public String editRun(@PathVariable String runId,
                          @RequestParam(required = false) Boolean saved,
                          Model model) {
        log.debug("editRun() | runId={}, saved={}", runId, saved);

        NewsletterRun run = newsletterRunPort.findByRunId(runId);

        model.addAttribute("run", run);
        model.addAttribute("htmlContent", run.htmlContent());
        model.addAttribute("saved", Boolean.TRUE.equals(saved));

        String timestamp = run.generatedAt() != null
                ? DISPLAY_FMT.format(run.generatedAt()) + " UTC"
                : "";
        model.addAttribute("generatedAtFormatted", timestamp);

        log.debug("editRun() | return=newsletter-edit");
        return "newsletter-edit";
    }

    /**
     * Saves the edited HTML content back to the newsletter run.
     *
     * <p>Regenerates the plain-text version by stripping HTML tags via Jsoup.
     * The run status remains DRAFT.  Redirects back to the edit page with
     * a {@code ?saved=true} flash parameter.
     *
     * @param runId       the run identifier.
     * @param htmlContent the edited HTML from TinyMCE.
     * @return redirect to the edit page.
     */
    @PostMapping("/{runId}/save")
    public String saveDraft(@PathVariable String runId,
                            @RequestParam String htmlContent) {
        log.debug("saveDraft() | runId={}, htmlContentLength={}", runId, htmlContent.length());

        NewsletterRun existing = newsletterRunPort.findByRunId(runId);

        String plainText = Jsoup.parse(htmlContent).text();

        NewsletterRun updated = new NewsletterRun(
                existing.runId(),
                existing.title(),
                existing.weekOf(),
                htmlContent,
                plainText,
                existing.status(),
                existing.generatedAt()
        );
        newsletterRunPort.save(updated);

        log.info("saveDraft() | Draft saved: runId={}", runId);
        log.debug("saveDraft() | return=redirect");
        return "redirect:/newsletter/runs/" + runId + "/edit?saved=true";
    }

    /**
     * Delivers the newsletter to all active subscribers and redirects to the
     * run list with a success flash.
     *
     * @param runId the run identifier.
     * @return redirect to the run list.
     */
    @PostMapping("/{runId}/send")
    public String sendNewsletter(@PathVariable String runId) {
        log.debug("sendNewsletter() | runId={}", runId);

        try {
            int count = deliverUseCase.deliver(runId);
            log.info("sendNewsletter() | Newsletter sent: runId={}, count={}", runId, count);
            log.debug("sendNewsletter() | return=redirect (sent)");
            return "redirect:/newsletter/runs?sent=true&count=" + count;
        } catch (Exception e) {
            log.error("sendNewsletter() | Delivery failed: runId={}, error={}", runId, e.getMessage());
            log.debug("sendNewsletter() | return=redirect (error)");
            return "redirect:/newsletter/runs?error=" + e.getMessage();
        }
    }
}
