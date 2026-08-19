package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.NewsletterGenerationScheduler;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineHealthService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin pipeline management page — lets admins trigger all data pipelines on demand.
 *
 * <p>Displays a card grid of every data-gathering pipeline in the system with
 * its schedule, description, and a manual trigger button. Pre-flight validation
 * warns about missing prerequisites before execution. Pipelines are triggered
 * directly from the browser via JavaScript fetch calls to their native endpoints.
 *
 * <p>Restricted to ADMIN role via SecurityConfig ({@code /admin/**}).
 *
 * @author  Bill Blackmon
 * @version 2.3
 * @since   2026-07-30
 * @updated 2026-08-19
 */
@Slf4j
@Controller
@RequestMapping("/admin/pipelines")
public class AdminPipelineController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss z")
                    .withZone(ZoneId.of("America/New_York"));

    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(ZoneId.of("America/New_York"));

    private final PipelineHealthService healthService;
    private final NewsletterGenerationScheduler newsletterScheduler;
    private final MarketDigestService marketDigestService;

    public AdminPipelineController(PipelineHealthService healthService,
                                   NewsletterGenerationScheduler newsletterScheduler,
                                   MarketDigestService marketDigestService) {
        log.debug("AdminPipelineController() | healthService={}, newsletterScheduler={}, marketDigestService={}",
                  healthService, newsletterScheduler, marketDigestService);
        this.healthService = healthService;
        this.newsletterScheduler = newsletterScheduler;
        this.marketDigestService = marketDigestService;
    }

    /**
     * Renders the pipeline management dashboard.
     *
     * @param model Thymeleaf model
     * @return the "admin-pipelines" view name
     */
    @GetMapping
    public String pipelines(Model model) {
        log.debug("pipelines()");

        List<PipelineInfo> pipelineList = buildPipelineList();

        // Pre-flight checks for all pipelines
        List<String> pipelineIds = new ArrayList<>();
        for (PipelineInfo p : pipelineList) {
            pipelineIds.add(p.id());
        }
        Map<String, List<String>> preFlightWarnings = healthService.preFlightCheckAll(pipelineIds);

        // Last run info for all pipelines
        Map<String, PipelineHealthService.PipelineRunRecord> lastRuns = healthService.getAllLastRuns();
        Map<String, String> lastRunSummaries = new HashMap<>();
        Map<String, String> lastRunTimes = new HashMap<>();
        Map<String, String> lastRunStatuses = new HashMap<>();
        for (Map.Entry<String, PipelineHealthService.PipelineRunRecord> entry : lastRuns.entrySet()) {
            PipelineHealthService.PipelineRunRecord run = entry.getValue();
            lastRunSummaries.put(entry.getKey(), formatRunSummary(run));
            lastRunTimes.put(entry.getKey(), SHORT_FMT.format(run.completedAt()));
            lastRunStatuses.put(entry.getKey(), run.status());
        }

        // Pipeline registry for JS: id → {triggerUrl, httpMethod, requiresCsrf}
        Map<String, Map<String, Object>> pipelineRegistry = new LinkedHashMap<>();
        for (PipelineInfo p : pipelineList) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("triggerUrl", p.triggerUrl());
            entry.put("httpMethod", p.httpMethod());
            entry.put("requiresCsrf", p.requiresCsrf());
            pipelineRegistry.put(p.id(), entry);
        }

        // Recent pipeline run history (persisted in DB)
        List<PipelineRunEvent> recentEvents = healthService.getRecentHistory(50);
        Map<Long, String> eventTimestamps = new HashMap<>();
        for (PipelineRunEvent event : recentEvents) {
            if (event.startedAt() != null) {
                eventTimestamps.put(event.id(), DISPLAY_FMT.format(event.startedAt()));
            }
        }

        model.addAttribute("pipelines", pipelineList);
        model.addAttribute("pipelineCount", pipelineList.size());
        model.addAttribute("serverTime", DISPLAY_FMT.format(Instant.now()));
        model.addAttribute("preFlightWarnings", preFlightWarnings);
        model.addAttribute("lastRunSummaries", lastRunSummaries);
        model.addAttribute("lastRunTimes", lastRunTimes);
        model.addAttribute("lastRunStatuses", lastRunStatuses);
        model.addAttribute("pipelineRegistry", pipelineRegistry);
        model.addAttribute("recentEvents", recentEvents);
        model.addAttribute("eventTimestamps", eventTimestamps);

        log.debug("pipelines() | return=admin-pipelines, count={}, recentEvents={}", pipelineList.size(), recentEvents.size());
        return "admin-pipelines";
    }

    /**
     * Returns pre-flight check results for all pipelines as JSON.
     *
     * @return map of pipeline ID to warnings list
     */
    @GetMapping("/preflight")
    @ResponseBody
    public ResponseEntity<Map<String, List<String>>> preflight() {
        log.debug("preflight()");
        List<PipelineInfo> pipelineList = buildPipelineList();
        List<String> ids = new ArrayList<>();
        for (PipelineInfo p : pipelineList) {
            ids.add(p.id());
        }
        Map<String, List<String>> result = healthService.preFlightCheckAll(ids);
        log.debug("preflight() | return={} entries", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Performs a dry-run check for a specific pipeline.
     *
     * @param id the pipeline identifier
     * @return dry-run result
     */
    @GetMapping("/dry-run/{id}")
    @ResponseBody
    public ResponseEntity<PipelineHealthService.DryRunResult> dryRun(@PathVariable String id) {
        log.debug("dryRun() | id={}", id);
        PipelineHealthService.DryRunResult result = healthService.dryRun(id);
        log.debug("dryRun() | return={}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Records a pipeline run result reported by the browser after direct
     * execution. Called by JavaScript after each pipeline fetch completes.
     *
     * @param id   the pipeline identifier
     * @param body run result details from the browser
     * @return acknowledgement
     */
    @PostMapping("/record/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> recordRun(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        log.debug("recordRun() | id={}, body={}", id, body);

        String status = (String) body.getOrDefault("status", "UNKNOWN");
        String message = (String) body.getOrDefault("message", "");
        long durationMs = body.containsKey("durationMs")
                ? ((Number) body.get("durationMs")).longValue() : 0;

        Instant completedAt = Instant.now();
        Instant startedAt = completedAt.minusMillis(durationMs);

        PipelineHealthService.PipelineRunRecord record;
        if ("SUCCESS".equals(status)) {
            record = PipelineHealthService.PipelineRunRecord.success(id, 1, startedAt, completedAt);
        } else {
            record = PipelineHealthService.PipelineRunRecord.failure(id, message, startedAt, completedAt);
        }
        healthService.recordRun(id, record);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("recorded", "true");
        log.debug("recordRun() | return=recorded");
        return ResponseEntity.ok(result);
    }

    /**
     * Returns last run info for all pipelines as JSON.
     *
     * @return map of pipeline ID to run record
     */
    @GetMapping("/last-runs")
    @ResponseBody
    public ResponseEntity<Map<String, PipelineHealthService.PipelineRunRecord>> lastRuns() {
        log.debug("lastRuns()");
        Map<String, PipelineHealthService.PipelineRunRecord> result = healthService.getAllLastRuns();
        log.debug("lastRuns() | return={} entries", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Manually triggers the full newsletter pipeline: ingest, generate, deliver.
     * Bypasses the auto-send override check — always sends.
     *
     * @return JSON result with draftId and recipient count
     */
    @PostMapping("/newsletter/generate-and-send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateAndSendNewsletter() {
        log.debug("generateAndSendNewsletter()");

        Instant start = Instant.now();
        try {
            newsletterScheduler.runDailyDraftGeneration();

            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Newsletter generated and sent");
            result.put("durationMs", durationMs);

            log.info("generateAndSendNewsletter() | Newsletter pipeline completed in {}ms", durationMs);
            log.debug("generateAndSendNewsletter() | return={}", result);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FAILED");
            result.put("message", ex.getMessage());
            result.put("durationMs", durationMs);

            log.error("generateAndSendNewsletter() | Pipeline failed: {}", ex.getMessage(), ex);
            log.debug("generateAndSendNewsletter() | return={}", result);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Manually triggers the daily market digest pipeline for today.
     * Idempotent — if a digest already exists for today it is returned unchanged.
     *
     * @return JSON result with entry count, date, and duration
     */
    @PostMapping("/market-digest/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateMarketDigest() {
        log.debug("generateMarketDigest()");

        Instant start = Instant.now();
        try {
            MarketDigest digest = marketDigestService.generateDailyDigest(LocalDate.now());

            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Market digest generated for " + digest.date()
                    + " with " + digest.entries().size() + " entries");
            result.put("entryCount", digest.entries().size());
            result.put("date", digest.date().toString());
            result.put("durationMs", durationMs);

            log.info("generateMarketDigest() | digest generated: date={}, entries={}",
                    digest.date(), digest.entries().size());
            log.debug("generateMarketDigest() | return={}", result);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FAILED");
            result.put("message", ex.getMessage());
            result.put("durationMs", durationMs);

            log.error("generateMarketDigest() | pipeline failed: {}", ex.getMessage(), ex);
            log.debug("generateMarketDigest() | return={}", result);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private String formatRunSummary(PipelineHealthService.PipelineRunRecord run) {
        if ("SUCCESS".equals(run.status())) {
            return "OK in " + run.durationMs() + "ms";
        } else if ("FAILED".equals(run.status())) {
            String err = run.errors().isEmpty() ? "unknown" : run.errors().get(0);
            if (err.length() > 80) err = err.substring(0, 80) + "...";
            return "Failed: " + err;
        } else {
            return run.itemsProcessed() + " processed, " + run.itemsFailed() + " failed";
        }
    }

    private List<PipelineInfo> buildPipelineList() {
        log.debug("buildPipelineList()");

        List<PipelineInfo> list = new ArrayList<>();

        list.add(new PipelineInfo("rss-feeds", "RSS Feed Harvest",
                "Harvests articles from all configured RSS feeds (ACADEMIC, REGULATORY, INDUSTRY). Runs relevance filter, saves to DB.",
                "Daily 04:00 UTC", "FeedHarvestScheduler",
                "/api/v1/monitoring/feeds", "POST", false, "~2 min", "Low"));

        list.add(new PipelineInfo("competitor", "Competitor Web Pages",
                "Scrapes competitor pages (Anthropic, Perplexity, Google, OpenAI) for content changes via SHA-256 hashing.",
                "Daily 05:00 UTC", "WebMonitoringScheduler",
                "/api/v1/monitoring/harvest", "POST", false, "~1 min", "Low"));

        list.add(new PipelineInfo("huggingface", "HuggingFace Model Discovery",
                "Discovers healthcare AI models from HuggingFace API with metadata (likes, library, card data).",
                "Daily 05:30 UTC", "WebMonitoringScheduler",
                "/api/v1/monitoring/huggingface", "POST", false, "~30 sec", "Low"));

        list.add(new PipelineInfo("regulatory", "Regulatory Harvest",
                "Harvests FDA 510(k), De Novo, and CMS rules from federal APIs. Deduplicates by reference number. Matches watchlists.",
                "Daily 04:30 UTC", "RegulatoryHarvestScheduler",
                "/monitoring/regulatory/harvest", "POST", false, "~1 min", "Low"));

        list.add(new PipelineInfo("clinical-trials", "Clinical Trial Harvest",
                "Harvests AI-related clinical trials from ClinicalTrials.gov. Deduplicates by NCT ID. Matches watchlists.",
                "Daily 05:00 UTC", "ClinicalTrialHarvestScheduler",
                "/monitoring/clinical-trials-harvest", "POST", true, "~1 min", "Low"));

        list.add(new PipelineInfo("wiki-compile", "Wiki Compilation",
                "Compiles recent articles into wiki pages via LLM with provenance and contradiction detection. Requires AI API key.",
                "After daily feed harvest", "FeedHarvestScheduler",
                "/monitoring/wiki/compile", "POST", false, "~5 min", "High (LLM cost)"));

        list.add(new PipelineInfo("wiki-lint", "Wiki Linting",
                "Checks wiki pages for orphans, broken cross-references, stale content (>30 days), missing provenance.",
                "Daily 08:00 UTC", "WikiLintScheduler",
                "/monitoring/wiki/lint", "POST", false, "~10 sec", "Minimal"));

        list.add(new PipelineInfo("legal-backfill", "Legal & Regulatory Backfill",
                "Backfills legal articles from CourtListener and PubMed, plus regulatory events. Configurable lookback window.",
                "Manual only", "None",
                "/monitoring/legal-backfill?days=30", "POST", false, "~3 min", "Medium"));

        list.add(new PipelineInfo("pubmed-backfill", "PubMed Article Backfill",
                "Queries PubMed E-utilities API across 11 predefined healthcare AI search queries.",
                "Manual only", "None",
                "/monitoring/backfill?fromYear=2024&toYear=2026&maxPerQuery=20", "POST", false, "~2 min", "Medium"));

        list.add(new PipelineInfo("trend-detection", "Trend Detection",
                "Analyzes keyword frequency across 30/90/180-day windows to identify rising, fading, and new trends.",
                "Weekly Sunday 08:00 UTC", "TrendDetectionScheduler",
                "/api/v1/trends/detect", "POST", false, "~30 sec", "Low"));

        list.add(new PipelineInfo("legal-trends", "Legal Trend Detection",
                "Detects trends in legal, policy, and regulatory articles via LLM-based topic extraction.",
                "Weekly Sunday 09:00 UTC", "LegalTrendScheduler",
                "/dashboard/legal/trends/detect", "POST", true, "~1 min", "Medium (LLM)"));

        list.add(new PipelineInfo("topic-summaries", "Topic Summary Generation",
                "Generates AI-powered 3-sentence summaries for each news topic. Requires AI API key.",
                "After daily feed harvest", "FeedHarvestScheduler",
                "/api/v1/monitoring/summaries", "POST", false, "~3 min", "High (LLM cost)"));

        list.add(new PipelineInfo("embedding", "Article Embedding",
                "Embeds new articles into vector store for semantic search. Skips already-embedded articles. Requires pgvector.",
                "Daily 07:00 UTC", "EmbeddingScheduler",
                "/api/v1/monitoring/embeddings", "POST", false, "~10 min", "High (CPU + API)"));

        list.add(new PipelineInfo("market-intelligence", "Market Intelligence Report",
                "Generates monthly competitive landscape report via AI. Writes HTML to NotebookLMDirectory/summaries/.",
                "Monthly 1st at 08:00 UTC", "MarketIntelligenceScheduler",
                "/api/v1/market-intelligence/refresh", "POST", false, "~2 min", "High (LLM cost)"));

        list.add(new PipelineInfo("newsletter-send", "Newsletter Generate & Send",
                "Runs the full newsletter pipeline on demand: ingest today's articles, generate draft via AI, and deliver to all active subscribers. Use this if the scheduled run failed.",
                "Daily midnight UTC", "NewsletterGenerationScheduler",
                "/admin/pipelines/newsletter/generate-and-send", "POST", true, "~30 sec", "High (LLM cost)"));

        list.add(new PipelineInfo("research-harvest", "Research Harvest (Perplexity)",
                "Runs the COMBINED research pipeline (Perplexity + Google) for all configured topics. Persists new articles to DB.",
                "Daily 06:00 & 12:00 UTC", "ResearchHarvestScheduler",
                "/api/v1/monitoring/research-harvest", "POST", false, "~5 min", "Medium (LLM)"));

        list.add(new PipelineInfo("company-discovery", "Company Discovery (Perplexity)",
                "Discovers AI healthcare companies via Perplexity API: broad discovery, structured extraction, cross-validation. Deduplicates against DB.",
                "Weekly Sunday 06:00 UTC", "CompanyDiscoveryScheduler",
                "/api/v1/monitoring/company-discovery", "POST", false, "~5 min", "Medium (LLM)"));

        list.add(new PipelineInfo("wiki-gap-analysis", "Wiki Gap Analysis",
                "Analyzes recent articles against wiki coverage to identify knowledge gaps with specific article references. Requires AI API key.",
                "Weekly after wiki lint", "FeedHarvestScheduler",
                "/monitoring/wiki/gap-analysis", "POST", false, "~2 min", "High (LLM cost)"));

        list.add(new PipelineInfo("market-digest", "Market Digest Generator",
                "Generates the daily AI healthcare market digest: researches news via Perplexity, classifies market impact via Claude, persists digest. Idempotent — safe to re-run for today.",
                "Daily 07:00 AM CT", "MarketDigestScheduler",
                "/admin/pipelines/market-digest/generate", "POST", true, "~3 min", "High (LLM cost)"));

        log.debug("buildPipelineList() | return={} pipelines", list.size());
        return list;
    }

    /**
     * Immutable record describing a data pipeline for the admin UI.
     */
    public record PipelineInfo(
            String id,
            String name,
            String description,
            String schedule,
            String schedulerClass,
            String triggerUrl,
            String httpMethod,
            boolean requiresCsrf,
            String estimatedDuration,
            String performanceImpact
    ) {}
}
