package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.PipelineStepStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelineRunEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized pipeline health service providing pre-flight validation,
 * run tracking, and dry-run support for admin pipeline management.
 *
 * <p>Pre-flight checks validate that required prerequisites (API keys,
 * vector store, network) are available before a pipeline runs. Run
 * tracking stores the last execution result per pipeline in memory.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-30
 * @updated 2026-08-04
 */
@Slf4j
@Service
public class PipelineHealthService {

    private final Environment env;
    private final boolean vectorStoreAvailable;
    private final boolean isH2;
    private final String anthropicApiKey;
    private final String openaiApiKey;
    private final String perplexityApiKey;
    private final java.nio.file.Path dotEnvPath;
    private final PipelineRunEventPort pipelineRunEventPort;
    private final ConcurrentHashMap<String, PipelineRunRecord> lastRuns = new ConcurrentHashMap<>();

    @Autowired
    public PipelineHealthService(Environment env,
                                 ObjectProvider<VectorStore> vectorStoreProvider,
                                 @Autowired(required = false) PipelineRunEventPort pipelineRunEventPort) {
        this(env, vectorStoreProvider, java.nio.file.Path.of(".env"), pipelineRunEventPort);
    }

    PipelineHealthService(Environment env,
                          ObjectProvider<VectorStore> vectorStoreProvider,
                          java.nio.file.Path dotEnvPath,
                          PipelineRunEventPort pipelineRunEventPort) {
        log.debug("PipelineHealthService() | env={}, vectorStoreProvider={}, pipelineRunEventPort={}",
                env, vectorStoreProvider, pipelineRunEventPort);
        this.env = env;
        this.dotEnvPath = dotEnvPath;
        this.pipelineRunEventPort = pipelineRunEventPort;
        this.anthropicApiKey = resolveKey(env, "ANTHROPIC_API_KEY", "spring.ai.anthropic.api-key");
        this.openaiApiKey = resolveKey(env, "OPENAI_API_KEY", "spring.ai.openai.api-key");
        this.perplexityApiKey = resolveKey(env, "PERPLEXITY_API_KEY", "aihealthcare.perplexity.api-key");
        this.vectorStoreAvailable = vectorStoreProvider.getIfAvailable() != null;
        String dsUrl = env.getProperty("spring.datasource.url", "");
        this.isH2 = dsUrl.contains("jdbc:h2:");
        log.debug("PipelineHealthService() | vectorStoreAvailable={}, isH2={}, anthropicKeyPresent={}, openaiKeyPresent={}, perplexityKeyPresent={}, dbBackedTracking={}",
                  vectorStoreAvailable, isH2, isKeyUsable(anthropicApiKey), isKeyUsable(openaiApiKey), isKeyUsable(perplexityApiKey), pipelineRunEventPort != null);
    }

    /**
     * Runs pre-flight validation for a given pipeline, returning a list
     * of warnings. An empty list means the pipeline is ready to run.
     *
     * @param pipelineId the pipeline identifier
     * @return list of warning messages; empty if all checks pass
     */
    public List<String> preFlightCheck(String pipelineId) {
        log.debug("preFlightCheck() | pipelineId={}", pipelineId);
        List<String> warnings = new ArrayList<>();

        switch (pipelineId) {
            case "wiki-compile":
            case "topic-summaries":
            case "market-intelligence":
                if (!isAnthropicKeyReady()) {
                    warnings.add("ANTHROPIC_API_KEY not configured — LLM calls will fail");
                }
                break;

            case "trend-detection":
                if (!isAnthropicKeyReady()) {
                    warnings.add("ANTHROPIC_API_KEY not configured — trend extraction will fail");
                }
                break;

            case "legal-trends":
                if (!isAnthropicKeyReady()) {
                    warnings.add("ANTHROPIC_API_KEY not configured — legal trend extraction will fail");
                }
                break;

            case "embedding":
                if (!vectorStoreAvailable) {
                    warnings.add("VectorStore not available — pgvector required");
                }
                if (isH2) {
                    warnings.add("Running on H2 — pgvector not supported");
                }
                if (!isOpenAiKeyReady()) {
                    warnings.add("OPENAI_API_KEY not configured — embedding API will fail");
                }
                break;

            case "company-discovery":
                if (!isPerplexityKeyReady()) {
                    warnings.add("PERPLEXITY_API_KEY not configured — company discovery will return 0 results");
                }
                break;

            case "wiki-gap-analysis":
                if (!isAnthropicKeyReady()) {
                    warnings.add("ANTHROPIC_API_KEY not configured — gap analysis will fail");
                }
                break;

            case "rss-feeds":
            case "competitor":
            case "huggingface":
            case "regulatory":
            case "clinical-trials":
            case "wiki-lint":
            case "legal-backfill":
            case "pubmed-backfill":
                // These only need network access — no API keys required
                break;

            default:
                break;
        }

        log.debug("preFlightCheck() | return={} warnings", warnings.size());
        return warnings;
    }

    /**
     * Returns pre-flight check results for all pipelines.
     *
     * @param pipelineIds list of pipeline IDs to check
     * @return map of pipeline ID to list of warnings
     */
    public Map<String, List<String>> preFlightCheckAll(List<String> pipelineIds) {
        log.debug("preFlightCheckAll() | pipelineIds={}", pipelineIds.size());
        Map<String, List<String>> results = new LinkedHashMap<>();
        for (String id : pipelineIds) {
            results.put(id, preFlightCheck(id));
        }
        log.debug("preFlightCheckAll() | return={} entries", results.size());
        return results;
    }

    /**
     * Records a pipeline run result for tracking.
     *
     * @param pipelineId the pipeline identifier
     * @param record     the run result record
     */
    public void recordRun(String pipelineId, PipelineRunRecord record) {
        log.debug("recordRun() | pipelineId={}, record={}", pipelineId, record);
        lastRuns.put(pipelineId, record);
        if (pipelineRunEventPort != null) {
            try {
                PipelineStepStatus status;
                if ("SUCCESS".equals(record.status())) {
                    status = PipelineStepStatus.SUCCESS;
                } else if ("FAILED".equals(record.status())) {
                    status = PipelineStepStatus.FAILED;
                } else {
                    status = PipelineStepStatus.SKIPPED;
                }
                String errorMessage = null;
                if (record.errors() != null && !record.errors().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < record.errors().size(); i++) {
                        if (i > 0) sb.append("; ");
                        sb.append(record.errors().get(i));
                    }
                    errorMessage = sb.toString();
                }
                PipelineRunEvent event = new PipelineRunEvent(
                        null, pipelineId, pipelineId, status,
                        record.startedAt(), record.completedAt(), record.durationMs(),
                        errorMessage, record.itemsProcessed(), "MANUAL",
                        null, null, null);
                pipelineRunEventPort.save(event);
            } catch (Exception e) {
                log.warn("recordRun() | failed to persist pipeline event: {}", e.getMessage());
            }
        }
        log.debug("recordRun() | return=void");
    }

    /**
     * Returns the last run record for a given pipeline, or null if never run.
     *
     * @param pipelineId the pipeline identifier
     * @return last run record, or null
     */
    public PipelineRunRecord getLastRun(String pipelineId) {
        log.debug("getLastRun() | pipelineId={}", pipelineId);
        if (pipelineRunEventPort != null) {
            try {
                List<PipelineRunEvent> events = pipelineRunEventPort.findByPipelineId(pipelineId, 1);
                if (!events.isEmpty()) {
                    PipelineRunRecord result = toRunRecord(events.get(0));
                    log.debug("getLastRun() | return={} (from DB)", result);
                    return result;
                }
            } catch (Exception e) {
                log.warn("getLastRun() | DB lookup failed, falling back to cache: {}", e.getMessage());
            }
        }
        PipelineRunRecord result = lastRuns.get(pipelineId);
        log.debug("getLastRun() | return={} (from cache)", result);
        return result;
    }

    /**
     * Returns last run records for all pipelines.
     *
     * @return map of pipeline ID to last run record
     */
    public Map<String, PipelineRunRecord> getAllLastRuns() {
        log.debug("getAllLastRuns()");
        if (pipelineRunEventPort != null) {
            try {
                Map<String, PipelineRunEvent> latestEvents = pipelineRunEventPort.findLatestPerPipeline();
                if (!latestEvents.isEmpty()) {
                    Map<String, PipelineRunRecord> result = new LinkedHashMap<>();
                    for (Map.Entry<String, PipelineRunEvent> entry : latestEvents.entrySet()) {
                        result.put(entry.getKey(), toRunRecord(entry.getValue()));
                    }
                    log.debug("getAllLastRuns() | return={} entries (from DB)", result.size());
                    return result;
                }
            } catch (Exception e) {
                log.warn("getAllLastRuns() | DB lookup failed, falling back to cache: {}", e.getMessage());
            }
        }
        Map<String, PipelineRunRecord> result = new LinkedHashMap<>(lastRuns);
        log.debug("getAllLastRuns() | return={} entries (from cache)", result.size());
        return result;
    }

    /**
     * Performs a dry-run check for a pipeline — validates prerequisites
     * and returns what would happen without actually executing.
     *
     * @param pipelineId the pipeline identifier
     * @return dry-run result with status and details
     */
    public DryRunResult dryRun(String pipelineId) {
        log.debug("dryRun() | pipelineId={}", pipelineId);
        List<String> warnings = preFlightCheck(pipelineId);
        String status;
        String detail;

        if (!warnings.isEmpty()) {
            status = "BLOCKED";
            StringBuilder sb = new StringBuilder("Cannot run: ");
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) sb.append("; ");
                sb.append(warnings.get(i));
            }
            detail = sb.toString();
        } else {
            status = "READY";
            detail = getDryRunDetail(pipelineId);
        }

        DryRunResult result = new DryRunResult(pipelineId, status, warnings, detail);
        log.debug("dryRun() | return={}", result);
        return result;
    }

    /**
     * Returns recent pipeline run events from the DB for the history table.
     * Returns an empty list if the DB port is not available.
     *
     * @param limit maximum number of events to return
     * @return list of recent pipeline run events, newest first
     */
    public List<PipelineRunEvent> getRecentHistory(int limit) {
        log.debug("getRecentHistory() | limit={}", limit);
        if (pipelineRunEventPort == null) {
            log.debug("getRecentHistory() | return=[] (port not available)");
            return Collections.emptyList();
        }
        try {
            List<PipelineRunEvent> result = pipelineRunEventPort.findRecent(limit);
            log.debug("getRecentHistory() | return={} events", result.size());
            return result;
        } catch (Exception e) {
            log.warn("getRecentHistory() | DB lookup failed: {}", e.getMessage());
            log.debug("getRecentHistory() | return=[] (DB error)");
            return Collections.emptyList();
        }
    }

    // --- private helpers ---

    private PipelineRunRecord toRunRecord(PipelineRunEvent event) {
        String status;
        if (event.status() == PipelineStepStatus.SUCCESS) {
            status = "SUCCESS";
        } else if (event.status() == PipelineStepStatus.FAILED) {
            status = "FAILED";
        } else {
            status = "SKIPPED";
        }
        List<String> errors;
        if (event.errorMessage() != null && !event.errorMessage().isBlank()) {
            errors = List.of(event.errorMessage());
        } else {
            errors = List.of();
        }
        int itemsFailed = (event.status() == PipelineStepStatus.FAILED) ? 1 : 0;
        return new PipelineRunRecord(
                event.pipelineId(), status, event.itemsProcessed(), itemsFailed,
                errors, event.startedAt(), event.completedAt(), event.durationMs());
    }

    private boolean isAnthropicKeyReady() {
        boolean ready = isKeyUsable(anthropicApiKey);
        log.debug("isAnthropicKeyReady() | keyPresent={}, ready={}", !anthropicApiKey.isBlank(), ready);
        return ready;
    }

    private boolean isPerplexityKeyReady() {
        boolean ready = isKeyUsable(perplexityApiKey);
        log.debug("isPerplexityKeyReady() | keyPresent={}, ready={}", !perplexityApiKey.isBlank(), ready);
        return ready;
    }

    private boolean isOpenAiKeyReady() {
        boolean ready = isKeyUsable(openaiApiKey);
        log.debug("isOpenAiKeyReady() | keyPresent={}, ready={}", !openaiApiKey.isBlank(), ready);
        return ready;
    }

    private boolean isKeyUsable(String key) {
        return key != null && !key.isBlank() && !key.startsWith("placeholder-set-");
    }

    private String resolveKey(Environment env, String envVarName, String springPropertyName) {
        log.debug("resolveKey() | envVarName={}, springPropertyName={}", envVarName, springPropertyName);
        // Try Environment property sources first
        String val = env.getProperty(envVarName, "");
        if (!val.isBlank()) {
            log.debug("resolveKey() | return=(resolved from env)");
            return val;
        }
        val = env.getProperty(springPropertyName, "");
        if (!val.isBlank()) {
            log.debug("resolveKey() | return=(resolved from spring property)");
            return val;
        }
        // Fallback: read .env file directly (Spring property resolution can miss some keys)
        val = readDotEnvKey(envVarName);
        log.debug("resolveKey() | return=(from .env fallback, found={})", !val.isBlank());
        return val;
    }

    private String readDotEnvKey(String keyName) {
        try {
            if (dotEnvPath == null || !java.nio.file.Files.exists(dotEnvPath)) {
                return "";
            }
            List<String> lines = java.nio.file.Files.readAllLines(dotEnvPath);
            for (String line : lines) {
                if (line.startsWith(keyName + "=")) {
                    return line.substring(keyName.length() + 1).trim();
                }
            }
        } catch (Exception e) {
            log.warn("readDotEnvKey() | failed to read .env for key={}: {}", keyName, e.getMessage());
        }
        return "";
    }

    private String getDryRunDetail(String pipelineId) {
        switch (pipelineId) {
            case "rss-feeds":
                return "Will harvest articles from all configured RSS feeds, run relevance filter, save new articles to DB";
            case "competitor":
                return "Will scrape competitor pages for content changes via SHA-256 hashing, save changed pages";
            case "huggingface":
                return "Will query HuggingFace API for healthcare AI models, save new model entries";
            case "regulatory":
                return "Will harvest FDA 510(k), De Novo, and CMS rules from federal APIs, deduplicate, match watchlists";
            case "clinical-trials":
                return "Will harvest AI-related clinical trials from ClinicalTrials.gov, deduplicate by NCT ID";
            case "wiki-compile":
                return "Will compile recent articles into wiki pages via LLM with provenance and contradiction detection";
            case "wiki-lint":
                return "Will check wiki pages for orphans, broken cross-references, stale content, missing provenance";
            case "legal-backfill":
                return "Will backfill legal articles from CourtListener and PubMed (30-day lookback window)";
            case "pubmed-backfill":
                return "Will query PubMed E-utilities API across 11 healthcare AI search queries";
            case "trend-detection":
                return "Will analyze keyword frequency across 30/90/180-day windows via LLM extraction";
            case "legal-trends":
                return "Will detect trends in legal/policy/regulatory articles via LLM-based topic extraction";
            case "topic-summaries":
                return "Will generate AI-powered 3-sentence summaries for each news topic";
            case "embedding":
                return "Will embed un-embedded articles into vector store (new articles only)";
            case "market-intelligence":
                return "Will generate monthly competitive landscape report via AI";
            case "company-discovery":
                return "Will discover AI healthcare companies via Perplexity API, extract structured fields, cross-validate, and persist new entries";
            case "wiki-gap-analysis":
                return "Will analyze recent articles against wiki pages to identify coverage gaps via LLM";
            default:
                return "Ready to execute";
        }
    }

    /**
     * Immutable record tracking the result of a pipeline run.
     */
    public record PipelineRunRecord(
            String pipelineId,
            String status,
            int itemsProcessed,
            int itemsFailed,
            List<String> errors,
            Instant startedAt,
            Instant completedAt,
            long durationMs
    ) {
        /**
         * Creates a success record.
         */
        public static PipelineRunRecord success(String pipelineId, int itemsProcessed,
                                                 Instant startedAt, Instant completedAt) {
            return new PipelineRunRecord(pipelineId, "SUCCESS", itemsProcessed, 0,
                    List.of(), startedAt, completedAt,
                    Duration.between(startedAt, completedAt).toMillis());
        }

        /**
         * Creates a failure record.
         */
        public static PipelineRunRecord failure(String pipelineId, String errorMessage,
                                                 Instant startedAt, Instant completedAt) {
            return new PipelineRunRecord(pipelineId, "FAILED", 0, 1,
                    List.of(errorMessage), startedAt, completedAt,
                    Duration.between(startedAt, completedAt).toMillis());
        }

        /**
         * Creates a partial success record.
         */
        public static PipelineRunRecord partial(String pipelineId, int processed, int failed,
                                                 List<String> errors,
                                                 Instant startedAt, Instant completedAt) {
            return new PipelineRunRecord(pipelineId, "PARTIAL", processed, failed,
                    errors, startedAt, completedAt,
                    Duration.between(startedAt, completedAt).toMillis());
        }
    }

    /**
     * Immutable record representing a dry-run check result.
     */
    public record DryRunResult(
            String pipelineId,
            String status,
            List<String> warnings,
            String detail
    ) {}
}
