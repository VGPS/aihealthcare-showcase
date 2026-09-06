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
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.NewsletterGenerationScheduler;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineHealthService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * @version 2.8
 * @since   2026-07-30
 * @updated 2026-09-06
 */
@Slf4j
@Controller
@RequestMapping("/admin/pipelines")
public class AdminPipelineController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss z")
                    .withZone(ZoneId.of("America/New_York"));

    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMM d z HH:mm:ss")
                    .withZone(ZoneId.of("America/Chicago"));

    private final PipelineHealthService healthService;
    private final NewsletterGenerationScheduler newsletterScheduler;
    private final MarketDigestService marketDigestService;
    private final DeliverNewsletterUseCase deliverUseCase;
    private final PriceReactionService priceReactionService;
    private final DetectTrendsUseCase detectTrendsUseCase;

    public AdminPipelineController(PipelineHealthService healthService,
                                   NewsletterGenerationScheduler newsletterScheduler,
                                   MarketDigestService marketDigestService,
                                   DeliverNewsletterUseCase deliverUseCase,
                                   PriceReactionService priceReactionService,
                                   DetectTrendsUseCase detectTrendsUseCase) {
        log.debug("AdminPipelineController() | healthService={}, newsletterScheduler={}, marketDigestService={}, deliverUseCase={}, priceReactionService={}, detectTrendsUseCase={}",
                  healthService, newsletterScheduler, marketDigestService, deliverUseCase, priceReactionService, detectTrendsUseCase);
        this.healthService = healthService;
        this.newsletterScheduler = newsletterScheduler;
        this.marketDigestService = marketDigestService;
        this.deliverUseCase = deliverUseCase;
        this.priceReactionService = priceReactionService;
        this.detectTrendsUseCase = detectTrendsUseCase;
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
            newsletterScheduler.runWeeklyDraftGeneration();

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
     * Manually triggers the FREE-tier digest send. Independent of the paid
     * newsletter pipeline above — builds a fresh digest and delivers it to
     * all active FREE subscribers. Use this if the scheduled daily run failed.
     *
     * @return JSON result with recipient count
     */
    @PostMapping("/digest/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendDigest() {
        log.debug("sendDigest()");

        Instant start = Instant.now();
        try {
            int recipientCount = deliverUseCase.deliverDigest();

            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Digest sent to " + recipientCount + " free subscribers");
            result.put("recipientCount", recipientCount);
            result.put("durationMs", durationMs);

            log.info("sendDigest() | Digest pipeline completed in {}ms, recipients={}", durationMs, recipientCount);
            log.debug("sendDigest() | return={}", result);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FAILED");
            result.put("message", ex.getMessage());
            result.put("durationMs", durationMs);

            log.error("sendDigest() | Pipeline failed: {}", ex.getMessage(), ex);
            log.debug("sendDigest() | return={}", result);
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

    /**
     * Manually triggers one pass of the price-reaction poller for all due,
     * uncaptured horizons across recent market digest entries.
     * Idempotent — skips horizons already captured.
     *
     * @return JSON result with snapshot count and duration
     */
    @PostMapping("/market-digest/price-reactions/capture")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> captureMarketPriceReactions() {
        log.debug("captureMarketPriceReactions()");

        Instant start = Instant.now();
        try {
            List<PriceReactionSnapshot> captured = priceReactionService.capturePendingReactions(start);

            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Captured " + captured.size() + " price-reaction snapshot(s)");
            result.put("snapshotCount", captured.size());
            result.put("durationMs", durationMs);

            log.info("captureMarketPriceReactions() | captured={}", captured.size());
            log.debug("captureMarketPriceReactions() | return={}", result);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FAILED");
            result.put("message", ex.getMessage());
            result.put("durationMs", durationMs);

            log.error("captureMarketPriceReactions() | failed: {}", ex.getMessage(), ex);
            log.debug("captureMarketPriceReactions() | return={}", result);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Manually triggers keyword trend detection — the same pipeline that runs
     * automatically on Sunday mornings. Analyzes keyword frequency across 30/90/180-day
     * windows, classifies rising/fading/new signals, generates Perplexity summaries,
     * and persists the snapshot. Runs synchronously — expect 15-20 minutes.
     *
     * @return JSON result with signal count and duration
     */
    @PostMapping("/trend-detection/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runTrendDetection() {
        log.debug("runTrendDetection()");

        Instant start = Instant.now();
        try {
            TrendSnapshot snapshot = detectTrendsUseCase.detectTrends();

            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Trend detection complete: " + snapshot.risingTopics().size()
                    + " rising, " + snapshot.fadingTopics().size() + " fading, "
                    + snapshot.newTopics().size() + " new signals");
            result.put("risingCount", snapshot.risingTopics().size());
            result.put("fadingCount", snapshot.fadingTopics().size());
            result.put("newCount", snapshot.newTopics().size());
            result.put("totalKeywords", snapshot.totalKeywords());
            result.put("durationMs", durationMs);

            log.info("runTrendDetection() | rising={}, fading={}, new={}, durationMs={}",
                     snapshot.risingTopics().size(), snapshot.fadingTopics().size(),
                     snapshot.newTopics().size(), durationMs);
            log.debug("runTrendDetection() | return={}", result);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FAILED");
            result.put("message", ex.getMessage());
            result.put("durationMs", durationMs);

            log.error("runTrendDetection() | pipeline failed: {}", ex.getMessage(), ex);
            log.debug("runTrendDetection() | return={}", result);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Syncs the NotebookLM corpus from EC2 to the local dev machine via SSH + SCP.
     *
     * <p>EC2 runs {@code ResearchHarvestScheduler} (06:00 &amp; 12:00 UTC daily),
     * continuously writing article
     * {@code .txt} files and date-stamped summaries into
     * {@code /opt/aihealthcare/NotebookLMDirectory}. This endpoint:
     * <ol>
     *   <li>SSH-creates a tar of all article files and the summaries directory on EC2.</li>
     *   <li>SCPs the tar to the local machine's temp directory.</li>
     *   <li>Extracts into {@code NotebookLMDirectory} — never overwriting files already
     *       present locally ({@code --keep-old-files}).</li>
     *   <li>Removes the temp tar from both sides.</li>
     * </ol>
     *
     * <p>Returns 503 immediately if the EC2 PEM key is not found at its expected
     * local path — this endpoint only functions on the dev machine, not when the
     * app is deployed to EC2 itself.
     *
     * @return JSON result with article and summary counts before/after sync
     */
    @PostMapping("/notebooklm/sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncNotebookLm() {
        log.debug("syncNotebookLm()");

        Instant start = Instant.now();
        Path keyPath = Paths.get("C:/workspaces/SpringAIClaude/N_VaKeyPair.pem");

        if (!Files.exists(keyPath)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FAILED");
            result.put("message", "EC2 PEM key not found at " + keyPath
                    + " — this pipeline only works on the local dev machine, not EC2.");
            result.put("durationMs", 0L);
            log.warn("syncNotebookLm() | key not found: {}", keyPath);
            log.debug("syncNotebookLm() | return=503");
            return ResponseEntity.status(503).body(result);
        }

        String key     = keyPath.toString();
        String ip      = "100.61.13.237";
        String ssh     = "C:/Windows/System32/OpenSSH/ssh.exe";
        String scp     = "C:/Windows/System32/OpenSSH/scp.exe";
        String tmpTar  = System.getProperty("java.io.tmpdir").replace('\\', '/') + "/notebooklm_full.tar.gz";
        String localDir = "NotebookLMDirectory";

        try {
            int articlesBefore  = countFiles(localDir, ".txt");
            int summariesBefore = countFiles(localDir + "/summaries", null);

            // Step 1 — create tar on EC2 (articles + summaries)
            log.info("syncNotebookLm() | step 1: creating EC2 tar");
            runProcess(ssh, "-i", key, "-o", "StrictHostKeyChecking=no",
                    "ec2-user@" + ip,
                    "cd /opt/aihealthcare/NotebookLMDirectory && tar -czf /tmp/notebooklm_full.tar.gz *.txt summaries/");

            // Step 2 — download tar
            log.info("syncNotebookLm() | step 2: downloading tar from EC2");
            runProcess(scp, "-i", key, "-o", "StrictHostKeyChecking=no",
                    "ec2-user@" + ip + ":/tmp/notebooklm_full.tar.gz", tmpTar);

            // Step 3 — extract locally, never overwriting existing files
            log.info("syncNotebookLm() | step 3: extracting to {}", localDir);
            runProcess("tar", "-xzf", tmpTar, "-C", localDir, "--keep-old-files");

            // Step 4 — cleanup
            Files.deleteIfExists(Paths.get(tmpTar));
            runProcess(ssh, "-i", key, "-o", "StrictHostKeyChecking=no",
                    "ec2-user@" + ip, "rm -f /tmp/notebooklm_full.tar.gz");

            int articlesAfter  = countFiles(localDir, ".txt");
            int summariesAfter = countFiles(localDir + "/summaries", null);
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Synced +" + (articlesAfter - articlesBefore) + " article files, +"
                    + (summariesAfter - summariesBefore) + " summary files ("
                    + articlesAfter + " articles and " + summariesAfter + " summaries total locally)");
            result.put("articlesBefore",  articlesBefore);
            result.put("articlesAfter",   articlesAfter);
            result.put("summariesBefore", summariesBefore);
            result.put("summariesAfter",  summariesAfter);
            result.put("durationMs", durationMs);

            log.info("syncNotebookLm() | complete: +{} articles, +{} summaries in {}ms",
                    articlesAfter - articlesBefore, summariesAfter - summariesBefore, durationMs);
            log.debug("syncNotebookLm() | return={}", result);
            return ResponseEntity.ok(result);

        } catch (Exception ex) {
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FAILED");
            result.put("message", ex.getMessage());
            result.put("durationMs", durationMs);
            log.error("syncNotebookLm() | sync failed: {}", ex.getMessage(), ex);
            log.debug("syncNotebookLm() | return=error");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Runs an external process and blocks until it exits. Throws {@link IOException}
     * with captured output if the exit code is non-zero.
     *
     * @param cmd command and arguments
     * @throws IOException          if the process fails or cannot start
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    private void runProcess(String... cmd) throws IOException, InterruptedException {
        log.debug("runProcess() | cmd={}", String.join(" ", cmd));
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        process.waitFor();
        int exit = process.exitValue();
        if (exit != 0) {
            throw new IOException("Command failed (exit=" + exit + "): " + new String(output).trim());
        }
        log.debug("runProcess() | return=void (exit=0)");
    }

    /**
     * Counts files in a directory, optionally filtered by extension suffix.
     *
     * @param dir       directory path (relative or absolute)
     * @param extension file extension filter (e.g. {@code ".txt"}); null counts all files
     * @return file count; 0 if directory does not exist
     */
    private int countFiles(String dir, String extension) {
        log.debug("countFiles() | dir={}, extension={}", dir, extension);
        File d = new File(dir);
        if (!d.exists() || !d.isDirectory()) {
            log.debug("countFiles() | return=0 (not found)");
            return 0;
        }
        String[] files = extension != null
                ? d.list((f, name) -> name.endsWith(extension))
                : d.list((f, name) -> new File(f, name).isFile());
        int count = files != null ? files.length : 0;
        log.debug("countFiles() | return={}", count);
        return count;
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

        // ── High LLM cost (most complex first) ──────────────────────────────
        list.add(new PipelineInfo("market-digest", "Market Digest Generator",
                "Generates the daily AI healthcare market digest: researches news via Perplexity, classifies market impact via Claude, persists digest. Idempotent — safe to re-run for today.",
                "Daily 07:00 AM CT", "MarketDigestScheduler",
                "/admin/pipelines/market-digest/generate", "POST", true, "~3 min", "High (LLM cost)"));

        list.add(new PipelineInfo("price-reaction-capture", "Market Price Reaction Capture",
                "Polls Alpaca for stock price moves at 1h/4h/1d/3d horizons after each qualifying market digest entry. Idempotent — skips horizons already captured. Run after market hours to pick up 1d/3d closes.",
                "Hourly (top of hour)", "MarketAnalysisScheduler",
                "/admin/pipelines/market-digest/price-reactions/capture", "POST", true, "~15 sec", "Low"));

        list.add(new PipelineInfo("wiki-compile", "Wiki Compilation",
                "Compiles recent articles into wiki pages via LLM with provenance and contradiction detection. Requires AI API key.",
                "After daily feed harvest", "FeedHarvestScheduler",
                "/monitoring/wiki/compile", "POST", false, "~5 min", "High (LLM cost)"));

        list.add(new PipelineInfo("newsletter-send", "Newsletter Generate & Send",
                "Runs the full paid-newsletter pipeline on demand: ingest the trailing week's articles across all topics, generate draft via AI, and deliver to ENTERPRISE/SUBSCRIBER/DEMO subscribers. Use this if the scheduled run failed. Does not send the FREE digest — see below.",
                "Weekly Monday 08:00 UTC", "NewsletterGenerationScheduler",
                "/admin/pipelines/newsletter/generate-and-send", "POST", true, "~30 sec", "High (LLM cost)"));

        list.add(new PipelineInfo("digest-send", "FREE Digest Send",
                "Builds a fresh digest (with Article of the Day) and delivers it to all active FREE subscribers. Independent of the paid newsletter pipeline above. Use this if the scheduled daily run failed.",
                "Daily midnight UTC", "DigestDeliveryScheduler",
                "/admin/pipelines/digest/send", "POST", true, "~15 sec", "High (LLM cost)"));

        list.add(new PipelineInfo("wiki-gap-analysis", "Wiki Gap Analysis",
                "Analyzes recent articles against wiki coverage to identify knowledge gaps with specific article references. Requires AI API key.",
                "Weekly after wiki lint", "FeedHarvestScheduler",
                "/monitoring/wiki/gap-analysis", "POST", false, "~2 min", "High (LLM cost)"));

        list.add(new PipelineInfo("topic-summaries", "Topic Summary Generation",
                "Generates AI-powered 3-sentence summaries for each news topic. Requires AI API key.",
                "After daily feed harvest", "FeedHarvestScheduler",
                "/api/v1/monitoring/summaries", "POST", false, "~3 min", "High (LLM cost)"));

        // ── High CPU + API ───────────────────────────────────────────────────
        list.add(new PipelineInfo("embedding", "Article Embedding",
                "Embeds new articles into vector store for semantic search. Skips already-embedded articles. Requires pgvector.",
                "Daily 07:00 UTC", "EmbeddingScheduler",
                "/api/v1/monitoring/embeddings", "POST", false, "~10 min", "High (CPU + API)"));

        // ── Medium LLM ───────────────────────────────────────────────────────
        list.add(new PipelineInfo("legal-trends", "Legal Trend Detection",
                "Detects trends in legal, policy, and regulatory articles via LLM-based topic extraction.",
                "Weekly Sunday 09:00 UTC", "LegalTrendScheduler",
                "/dashboard/legal/trends/detect", "POST", true, "~1 min", "Medium (LLM)"));

        list.add(new PipelineInfo("research-harvest", "Research Harvest (Perplexity)",
                "Runs the COMBINED research pipeline (Perplexity + Google) for all configured topics. Persists new articles to DB.",
                "Daily 06:00 & 12:00 UTC", "ResearchHarvestScheduler",
                "/api/v1/monitoring/research-harvest", "POST", false, "~5 min", "Medium (LLM)"));

        list.add(new PipelineInfo("company-discovery", "Company Discovery (Perplexity)",
                "Discovers AI healthcare companies via Perplexity API: broad discovery, structured extraction, cross-validation. Deduplicates against DB.",
                "Weekly Sunday 06:00 UTC", "CompanyDiscoveryScheduler",
                "/api/v1/monitoring/company-discovery", "POST", false, "~5 min", "Medium (LLM)"));

        list.add(new PipelineInfo("deal-signals", "Deal Signal Detection",
                "Keyword-scans the last 7 days of articles for funding/acquisition/partnership/IPO/product-launch signals, then sends matches to Claude for confirmation and enrichment (amount, counterparty, analysis). Falls back to keyword-only classification only when no LLM port is configured — not when an LLM call transiently fails, so a bad API key can silently drop keyword-matched candidates. Previously only ran as a step inside Run Full Cascade with no way to test it in isolation.",
                "Manual only (also runs inside Run Full Cascade)", "StartupPipelineOrchestrator (cascade step)",
                "/api/v1/deals/detect", "POST", false, "~1-3 min", "Medium (LLM)"));

        // ── Medium (no LLM) ──────────────────────────────────────────────────
        list.add(new PipelineInfo("legal-backfill", "Legal & Regulatory Backfill",
                "Backfills legal articles from CourtListener and PubMed, plus regulatory events. Configurable lookback window.",
                "Manual only", "None",
                "/monitoring/legal-backfill?days=30", "POST", false, "~3 min", "Medium"));

        list.add(new PipelineInfo("pubmed-backfill", "PubMed Article Backfill",
                "Queries PubMed E-utilities API across 11 predefined healthcare AI search queries.",
                "Manual only", "None",
                "/monitoring/backfill?fromYear=2024&toYear=2026&maxPerQuery=20", "POST", false, "~2 min", "Medium"));

        // ── Low ──────────────────────────────────────────────────────────────
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

        list.add(new PipelineInfo("trend-detection", "Trend Detection",
                "Analyzes keyword frequency across 30/90/180-day windows to identify rising, fading, and new trends. LLM scores articles per keyword then calls Perplexity Deep Research for analyst summaries — runs synchronously, expect 15-20 minutes.",
                "Weekly Sunday 08:00 UTC", "TrendDetectionScheduler",
                "/admin/pipelines/trend-detection/run", "POST", true, "~15-20 min", "High (LLM cost)"));

        // ── Minimal ──────────────────────────────────────────────────────────
        list.add(new PipelineInfo("wiki-lint", "Wiki Linting",
                "Checks wiki pages for orphans, broken cross-references, stale content (>30 days), missing provenance.",
                "Daily 08:00 UTC", "WikiLintScheduler",
                "/monitoring/wiki/lint", "POST", false, "~10 sec", "Minimal"));

        list.add(new PipelineInfo("notebooklm-sync", "Download NotebookLM Corpus",
                "Syncs the NotebookLM corpus from EC2 to this local machine via SSH + SCP. " +
                "EC2 runs ResearchHarvestScheduler (06:00 & 12:00 UTC daily), " +
                "continuously writing article .txt files and date-stamped summaries into /opt/aihealthcare/NotebookLMDirectory. " +
                "This pipeline: (1) SSH-tars all EC2 article files and the summaries/ directory into /tmp/notebooklm_full.tar.gz, " +
                "(2) SCPs the tar to this machine's temp dir, " +
                "(3) extracts into local NotebookLMDirectory — never overwriting files already present locally, " +
                "(4) removes /tmp artifacts from both sides. " +
                "Run this after the local app has been dark for several days, or before uploading to Google NotebookLM. " +
                "LOCAL-ONLY: returns 503 when triggered from EC2 (PEM key not present there). " +
                "Also available as /goDownloadNotebookLM Claude skill for dev-machine use.",
                "Manual only", "goDownloadNotebookLM (Claude skill)",
                "/admin/pipelines/notebooklm/sync", "POST", true, "~2-3 min (SSH + SCP)", "Low"));

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
