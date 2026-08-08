package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchPlan;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.ResearchRun;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.model.RetrievalQuery;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;
import com.wgblackmon.aihealthcare.domain.model.VendorCompareResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.CompareVendorsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchExportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Application-layer service that implements {@link ConductResearchUseCase} and
 * {@link CompareVendorsUseCase}.
 *
 * <p>This service is the single entry point for the research pipeline.  It selects the
 * active execution mode ({@code LEGACY_GOOGLE} or {@code STAGED_RESEARCH}), dispatches
 * to the correct pipeline branch, and returns a fully assembled {@link ResearchAnswer}.
 *
 * <p><b>LEGACY_GOOGLE path</b> (always safe, no API keys required):
 * <ol>
 *   <li>Build a single {@link RetrievalQuery} from the request.</li>
 *   <li>Call {@code legacyAdapter.retrieve()} to fetch stored articles.</li>
 *   <li>Assemble citations and wrap in a single-section {@link ResearchAnswer}.</li>
 *   <li>Persist a {@link ResearchRun} tracking record.</li>
 * </ol>
 *
 * <p><b>STAGED_RESEARCH path</b> (requires AI + optional Perplexity API key):
 * <ol>
 *   <li>{@link ResearchPlanningService#plan} decomposes the query into sub-queries.</li>
 *   <li>Each sub-query is dispatched to {@code perplexityAdapter.retrieve()}.</li>
 *   <li>All sources are pooled and assembled into citations by {@link CitationAssembler}.</li>
 *   <li>{@link ResearchSynthesisService#synthesize} calls the AI to produce structured sections.</li>
 *   <li>New source articles are persisted via {@link ArticleStoragePort} (dedup by URL).</li>
 *   <li>Articles are exported to the NotebookLM corpus via {@link ResearchExportPort}.</li>
 *   <li>A {@link ResearchRun} tracking record is persisted via {@link ResearchRunPort}.</li>
 * </ol>
 *
 * <p>The effective mode is: {@code request.mode()} if non-null, else {@code defaultMode}.
 *
 * <p>This class is not annotated with {@code @Service} — it is wired as a bean
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 2.1
 * @since   2026-05-04
 * @updated 2026-07-07
 */
@Slf4j
public class ResearchOrchestratorService implements ConductResearchUseCase, CompareVendorsUseCase {

    private final ResearchMode              defaultMode;
    private final SourceRetrievalPort       legacyAdapter;
    private final SourceRetrievalPort       perplexityAdapter;
    private final ResearchPlanningService   planningService;
    private final ResearchSynthesisService  synthesisService;
    private final CitationAssembler         citationAssembler;
    private final ArticleStoragePort        articleStoragePort;
    private final ResearchRunPort           researchRunPort;
    private final ResearchExportPort        researchExportPort;
    private final VendorAssessmentService   vendorAssessmentService;

    /**
     * Constructs the orchestrator with all pipeline and persistence dependencies.
     *
     * @param defaultMode        Configured default when the request carries a {@code null} mode.
     * @param legacyAdapter      {@link SourceRetrievalPort} backed by stored article ingestion.
     * @param perplexityAdapter  {@link SourceRetrievalPort} backed by Perplexity Sonar.
     * @param planningService    AI-based query decomposition service.
     * @param synthesisService   AI-based source synthesis service.
     * @param citationAssembler  Pure-Java citation deduplication and numbering service.
     * @param articleStoragePort Port for persisting new Perplexity-sourced articles.
     * @param researchRunPort    Port for persisting the research run audit record.
     * @param researchExportPort      Port for exporting articles to the NotebookLM corpus.
     * @param vendorAssessmentService Domain service for vendor-structured AI synthesis.
     */
    public ResearchOrchestratorService(ResearchMode defaultMode,
                                       SourceRetrievalPort legacyAdapter,
                                       SourceRetrievalPort perplexityAdapter,
                                       ResearchPlanningService planningService,
                                       ResearchSynthesisService synthesisService,
                                       CitationAssembler citationAssembler,
                                       ArticleStoragePort articleStoragePort,
                                       ResearchRunPort researchRunPort,
                                       ResearchExportPort researchExportPort,
                                       VendorAssessmentService vendorAssessmentService) {
        log.debug("ResearchOrchestratorService() | defaultMode={}, legacyAdapter={}, "
                  + "perplexityAdapter={}, planningService={}, synthesisService={}",
                  defaultMode,
                  legacyAdapter.getClass().getSimpleName(),
                  perplexityAdapter.getClass().getSimpleName(),
                  planningService.getClass().getSimpleName(),
                  synthesisService.getClass().getSimpleName());
        this.defaultMode             = defaultMode;
        this.legacyAdapter           = legacyAdapter;
        this.perplexityAdapter       = perplexityAdapter;
        this.planningService         = planningService;
        this.synthesisService        = synthesisService;
        this.citationAssembler       = citationAssembler;
        this.articleStoragePort      = articleStoragePort;
        this.researchRunPort         = researchRunPort;
        this.researchExportPort      = researchExportPort;
        this.vendorAssessmentService = vendorAssessmentService;
        log.debug("ResearchOrchestratorService() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Routes to the LEGACY_GOOGLE, STAGED_RESEARCH, or COMBINED branch based on the
     * effective mode, then persists a {@link ResearchRun} tracking record.  For
     * STAGED_RESEARCH and COMBINED runs the Perplexity-sourced articles are also saved
     * as {@link NewsArticle} records and exported to the NotebookLM corpus.
     */
    @Override
    public ResearchAnswer conduct(ResearchRequest request) {
        log.debug("conduct() | request={}", request);

        ResearchMode effectiveMode = request.mode() != null ? request.mode() : defaultMode;
        log.info("conduct() | effectiveMode={}, query='{}'", effectiveMode, request.query());

        PipelineResult pipelineResult;
        if (effectiveMode == ResearchMode.STAGED_RESEARCH) {
            pipelineResult = conductStaged(request);
        } else if (effectiveMode == ResearchMode.COMBINED) {
            pipelineResult = conductCombined(request);
        } else {
            pipelineResult = conductLegacy(request);
        }

        ResearchAnswer result = pipelineResult.answer();
        log.info("conduct() | answer assembled: answerId={}, sections={}, citations={}",
                 result.answerId(), result.sections().size(), result.allCitations().size());

        // Persist new Perplexity articles + export for STAGED_RESEARCH and COMBINED.
        // LEGACY_GOOGLE sources are already in the DB; re-saving is unnecessary.
        // In COMBINED mode pipelineResult.sources() holds only the Perplexity-sourced articles.
        if ((effectiveMode == ResearchMode.STAGED_RESEARCH || effectiveMode == ResearchMode.COMBINED)
                && !pipelineResult.sources().isEmpty()) {
            List<NewsArticle> articles = toNewsArticles(pipelineResult.sources(), request.query());
            log.info("conduct() | persisting {} Perplexity articles to DB", articles.size());
            articleStoragePort.save(articles);
            log.info("conduct() | exporting {} articles to NotebookLM corpus", articles.size());
            researchExportPort.export("Research: " + request.query(), articles);
        }

        // Always save a ResearchRun tracking record regardless of mode.
        ResearchRun run = new ResearchRun(
                result.answerId(),
                request.query(),
                effectiveMode.name(),
                result.allCitations().size(),
                result.generatedAt());
        researchRunPort.save(run);
        log.info("conduct() | ResearchRun persisted: runId={}", run.runId());

        log.debug("conduct() | return={}", result.answerId());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs the COMBINED retrieval pipeline (Perplexity + legacy DB) to gather sources,
     * then delegates synthesis to {@link VendorAssessmentService} which produces one
     * {@link VendorAssessment} per vendor identified in the retrieved corpus.
     * No {@link ResearchRun} record is persisted — vendor comparisons are transient.
     */
    @Override
    public VendorCompareResult compare(String query, int maxSources, int minVendors, String scoring) {
        log.debug("compare() | query={}, maxSources={}, minVendors={}, scoring={}", query, maxSources, minVendors, scoring);

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (maxSources < 1) {
            throw new IllegalArgumentException("maxSources must be >= 1");
        }

        ResearchRequest request = new ResearchRequest(query, ResearchMode.COMBINED, null, maxSources);

        // Plan the query decomposition
        ResearchPlan plan = planningService.plan(query, null);
        log.info("compare() | plan produced: subQueryCount={}", plan.subQueries().size());

        // Perplexity retrieval
        List<RetrievedSource> perplexitySources = new ArrayList<>();
        for (String subQuery : plan.subQueries()) {
            RetrievalQuery rq = new RetrievalQuery(
                    subQuery, "PERPLEXITY",
                    Math.max(1, request.maxSources() / plan.subQueries().size()));
            List<RetrievedSource> batch = perplexityAdapter.retrieve(rq);
            log.debug("compare() | sub-query='{}' → {} Perplexity sources", subQuery, batch.size());
            perplexitySources.addAll(batch);
        }

        // Legacy retrieval for historical depth
        RetrievalQuery legacyQuery = new RetrievalQuery(query, "GOOGLE", maxSources);
        List<RetrievedSource> legacySources = legacyAdapter.retrieve(legacyQuery);
        log.info("compare() | legacy retrieved {} sources", legacySources.size());

        // Filter legacy sources to only those whose title or snippet mentions the query
        List<RetrievedSource> relevantLegacy = filterByQueryRelevance(legacySources, query);
        log.info("compare() | {} legacy sources relevant to query (filtered from {})",
                 relevantLegacy.size(), legacySources.size());

        // Merge — Perplexity first (higher relevance weight)
        List<RetrievedSource> allSources = new ArrayList<>();
        allSources.addAll(perplexitySources);
        allSources.addAll(relevantLegacy);
        log.info("compare() | merged {} total sources before dedup", allSources.size());

        // Assemble citations — CitationAssembler deduplicates by URL
        List<SourceCitation> citations = citationAssembler.assemble(allSources);
        log.info("compare() | {} citations after dedup", citations.size());

        // Vendor-structured synthesis
        List<VendorAssessment> vendors = vendorAssessmentService.assess(query, allSources, citations, minVendors, scoring);

        log.info("compare() | vendor assessment complete: vendorCount={}", vendors.size());
        VendorCompareResult result = new VendorCompareResult(vendors, citations);
        log.debug("compare() | return={} vendors, {} citations", vendors.size(), citations.size());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches articles directly per vendor topic name, bypassing query decomposition
     * and the lossy {@code filterByQueryRelevance} step.  Each vendor topic maps to a
     * feed topic in {@code application.yml} (e.g. "Anthropic Healthcare").
     */
    @Override
    public VendorCompareResult compareSelected(List<String> vendorTopics, String focusArea,
                                                int maxSources, String scoring) {
        log.debug("compareSelected() | vendorTopics={}, focusArea={}, maxSources={}, scoring={}",
                  vendorTopics, focusArea, maxSources, scoring);

        if (vendorTopics == null || vendorTopics.isEmpty()) {
            throw new IllegalArgumentException("vendorTopics must not be empty");
        }
        if (maxSources < 1) {
            throw new IllegalArgumentException("maxSources must be >= 1");
        }

        // Fetch articles directly per vendor topic — no query decomposition needed
        int perVendorLimit = Math.max(1, maxSources / vendorTopics.size());
        List<RetrievedSource> allSources = new ArrayList<>();

        for (String vendorTopic : vendorTopics) {
            RetrievalQuery rq = new RetrievalQuery(vendorTopic, "GOOGLE", perVendorLimit);
            List<RetrievedSource> vendorSources = legacyAdapter.retrieve(rq);
            log.info("compareSelected() | topic='{}' → {} sources", vendorTopic, vendorSources.size());
            allSources.addAll(vendorSources);
        }

        log.info("compareSelected() | total sources from DB: {}", allSources.size());

        // Build the query string for the AI prompt
        String query = buildVendorQuery(vendorTopics, focusArea);

        // Assemble citations
        List<SourceCitation> citations = citationAssembler.assemble(allSources);
        log.info("compareSelected() | {} citations after dedup", citations.size());

        // Extract short vendor names from topic names for the prompt
        List<String> vendorNames = new ArrayList<>();
        for (String topic : vendorTopics) {
            vendorNames.add(extractVendorName(topic));
        }

        // Vendor-structured synthesis with pre-specified vendor names
        List<VendorAssessment> vendors = vendorAssessmentService.assess(
                query, allSources, citations, vendorNames.size(), scoring, vendorNames);

        log.info("compareSelected() | vendor assessment complete: vendorCount={}", vendors.size());
        VendorCompareResult result = new VendorCompareResult(vendors, citations);
        log.debug("compareSelected() | return={} vendors, {} citations", vendors.size(), citations.size());
        return result;
    }

    /**
     * Builds a query string from vendor topics and an optional focus area.
     */
    private String buildVendorQuery(List<String> vendorTopics, String focusArea) {
        log.debug("buildVendorQuery() | vendorTopics={}, focusArea={}", vendorTopics, focusArea);

        StringBuilder sb = new StringBuilder("Compare ");
        for (int i = 0; i < vendorTopics.size(); i++) {
            if (i > 0 && i == vendorTopics.size() - 1) {
                sb.append(" and ");
            } else if (i > 0) {
                sb.append(", ");
            }
            sb.append(extractVendorName(vendorTopics.get(i)));
        }
        sb.append(" in healthcare AI");
        if (focusArea != null && !focusArea.isBlank()) {
            sb.append(", focusing on ").append(focusArea.trim());
        }

        String result = sb.toString();
        log.debug("buildVendorQuery() | return={}", result);
        return result;
    }

    /**
     * Extracts a short vendor name from a feed topic name.
     * E.g. "Anthropic Healthcare" → "Anthropic", "Amazon Connect Health" → "Amazon/AWS".
     */
    private String extractVendorName(String topicName) {
        log.debug("extractVendorName() | topicName={}", topicName);
        String result = topicName;
        if (topicName.endsWith(" Healthcare")) {
            result = topicName.substring(0, topicName.length() - " Healthcare".length());
        } else if (topicName.startsWith("Amazon Connect")) {
            result = "Amazon/AWS";
        }
        log.debug("extractVendorName() | return={}", result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Pipeline branches
    // -------------------------------------------------------------------------

    /**
     * LEGACY_GOOGLE pipeline: fetch stored articles, assemble into a single-section answer.
     * No AI planning or synthesis is performed.
     */
    private PipelineResult conductLegacy(ResearchRequest request) {
        log.debug("conductLegacy() | query={}", request.query());

        String topic = request.topicHint() != null && !request.topicHint().isBlank()
                ? request.topicHint()
                : request.query();

        RetrievalQuery query = new RetrievalQuery(topic, "GOOGLE", request.maxSources());
        List<RetrievedSource> sources = legacyAdapter.retrieve(query);
        log.info("conductLegacy() | retrieved {} sources", sources.size());

        List<SourceCitation> citations = citationAssembler.assemble(sources);

        String body = buildLegacySectionBody(sources, citations);
        ResearchSection section = new ResearchSection("Research Findings", body, citations);

        ResearchAnswer result = new ResearchAnswer(
                UUID.randomUUID().toString(),
                request.query(),
                Collections.singletonList(section),
                citations,
                Instant.now());

        log.debug("conductLegacy() | return=ResearchAnswer[sections=1, citations={}]",
                  citations.size());
        return new PipelineResult(result, sources);
    }

    /**
     * STAGED_RESEARCH pipeline: plan → retrieve → synthesize → cite.
     */
    private PipelineResult conductStaged(ResearchRequest request) {
        log.debug("conductStaged() | query={}", request.query());

        // Step 1: plan
        ResearchPlan plan = planningService.plan(request.query(), request.topicHint());
        log.info("conductStaged() | plan produced: subQueryCount={}", plan.subQueries().size());

        // Step 2: retrieve for each sub-query
        List<RetrievedSource> allSources = new ArrayList<>();
        for (String subQuery : plan.subQueries()) {
            RetrievalQuery rq = new RetrievalQuery(
                    subQuery, "PERPLEXITY",
                    Math.max(1, request.maxSources() / plan.subQueries().size()));
            List<RetrievedSource> batch = perplexityAdapter.retrieve(rq);
            log.debug("conductStaged() | sub-query='{}' → {} sources", subQuery, batch.size());
            allSources.addAll(batch);
        }

        // Fall back to legacy adapter when Perplexity returns nothing
        if (allSources.isEmpty()) {
            log.warn("conductStaged() | Perplexity returned no sources — falling back to legacy adapter");
            RetrievalQuery fallback = new RetrievalQuery(
                    request.query(), "GOOGLE", request.maxSources());
            allSources = legacyAdapter.retrieve(fallback);
            log.info("conductStaged() | legacy fallback retrieved {} sources", allSources.size());
        }

        // Step 3: assemble citations
        List<SourceCitation> citations = citationAssembler.assemble(allSources);

        // Step 4: synthesize
        ResearchAnswer result = synthesisService.synthesize(
                request.query(), plan, allSources, citations);

        log.debug("conductStaged() | return=ResearchAnswer[sections={}, citations={}]",
                  result.sections().size(), result.allCitations().size());
        return new PipelineResult(result, allSources);
    }

    /**
     * COMBINED pipeline: plan → retrieve from both Perplexity and legacy DB → merge
     * → deduplicate → AI-synthesize across the full combined source set.
     *
     * <p>Perplexity sub-queries are based on the AI-decomposed plan, giving them
     * semantic relevance to the query.  The legacy retrieval runs against the full
     * query string to provide historical depth.  Sources are merged with Perplexity
     * first so that URL deduplication in {@link CitationAssembler} retains the
     * higher-weight Perplexity entry when both backends return the same URL.
     *
     * <p>Only Perplexity-sourced articles are returned in the {@link PipelineResult}
     * for persistence — legacy sources are already present in the {@code news_articles}
     * table and do not need to be re-saved.
     */
    private PipelineResult conductCombined(ResearchRequest request) {
        log.debug("conductCombined() | query={}", request.query());

        // Step 1: plan (same decomposition as STAGED)
        ResearchPlan plan = planningService.plan(request.query(), request.topicHint());
        log.info("conductCombined() | plan produced: subQueryCount={}", plan.subQueries().size());

        // Step 2: Perplexity retrieval for each sub-query
        List<RetrievedSource> perplexitySources = new ArrayList<>();
        for (String subQuery : plan.subQueries()) {
            RetrievalQuery rq = new RetrievalQuery(
                    subQuery, "PERPLEXITY",
                    Math.max(1, request.maxSources() / plan.subQueries().size()));
            List<RetrievedSource> batch = perplexityAdapter.retrieve(rq);
            log.debug("conductCombined() | sub-query='{}' → {} Perplexity sources", subQuery, batch.size());
            perplexitySources.addAll(batch);
        }
        log.info("conductCombined() | Perplexity retrieved {} total sources", perplexitySources.size());

        // Step 3: legacy retrieval for historical depth
        RetrievalQuery legacyQuery = new RetrievalQuery(
                request.query(), "GOOGLE", request.maxSources());
        List<RetrievedSource> legacySources = legacyAdapter.retrieve(legacyQuery);
        log.info("conductCombined() | legacy retrieved {} sources", legacySources.size());

        // Step 4: merge — Perplexity first (higher relevance weight)
        List<RetrievedSource> allSources = new ArrayList<>();
        allSources.addAll(perplexitySources);
        allSources.addAll(legacySources);
        log.info("conductCombined() | merged {} total sources before dedup", allSources.size());

        // Step 5: assemble citations — CitationAssembler deduplicates by URL
        List<SourceCitation> citations = citationAssembler.assemble(allSources);
        log.info("conductCombined() | {} citations after dedup", citations.size());

        // Step 6: synthesize across the full combined source set
        ResearchAnswer result = synthesisService.synthesize(
                request.query(), plan, allSources, citations);

        log.debug("conductCombined() | return=ResearchAnswer[sections={}, citations={}]",
                  result.sections().size(), result.allCitations().size());
        // Return only Perplexity sources — legacy sources are already in the DB
        return new PipelineResult(result, perplexitySources);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Bundles a pipeline answer with the raw sources so conduct() can persist them. */
    private record PipelineResult(ResearchAnswer answer, List<RetrievedSource> sources) {}

    /** Build a plain-text section body listing titles and URLs for the legacy path. */
    private String buildLegacySectionBody(List<RetrievedSource> sources,
                                          List<SourceCitation> citations) {
        log.debug("buildLegacySectionBody() | sourceCount={}", sources.size());

        if (sources.isEmpty()) {
            String result = "No articles found for this query in the ingestion database. "
                    + "Try running a harvest first or switch to STAGED_RESEARCH mode.";
            log.debug("buildLegacySectionBody() | return=empty-message");
            return result;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("The following ").append(sources.size())
          .append(" article(s) were retrieved from the ingestion database:\n\n");

        for (SourceCitation citation : citations) {
            sb.append("[").append(citation.citationNumber()).append("] ")
              .append(citation.title()).append("\n")
              .append(citation.url()).append("\n\n");
        }

        String result = sb.toString().trim();
        log.debug("buildLegacySectionBody() | return={} chars", result.length());
        return result;
    }

    /**
     * Filters a list of retrieved sources to only those whose title or snippet
     * contains any word from the query (case-insensitive).  This prevents the
     * legacy fallback (which fetches all DB articles when no topic matches) from
     * injecting irrelevant articles into the vendor-compare prompt.
     */
    private List<RetrievedSource> filterByQueryRelevance(List<RetrievedSource> sources,
                                                          String query) {
        log.debug("filterByQueryRelevance() | sources={}, query={}", sources.size(), query);

        String[] queryWords = query.toLowerCase().split("\\s+");
        List<RetrievedSource> result = new ArrayList<>();

        for (RetrievedSource source : sources) {
            String title   = source.title()   != null ? source.title().toLowerCase()   : "";
            String snippet = source.snippet() != null ? source.snippet().toLowerCase() : "";

            boolean relevant = false;
            for (String word : queryWords) {
                if (word.length() < 3) {
                    continue; // skip short words like "AI", "in", "of"
                }
                if (title.contains(word) || snippet.contains(word)) {
                    relevant = true;
                    break;
                }
            }
            if (relevant) {
                result.add(source);
            }
        }

        log.debug("filterByQueryRelevance() | return={} relevant sources", result.size());
        return result;
    }

    /**
     * Maps {@link RetrievedSource} records from the research pipeline to
     * {@link NewsArticle} records suitable for persistence and vector embedding.
     *
     * <p>Mapping rules:
     * <ul>
     *   <li>{@code sourceId}   → {@code articleId}</li>
     *   <li>{@code title}      → {@code title}</li>
     *   <li>{@code url}        → {@code URI} (empty URI on parse error)</li>
     *   <li>{@code snippet}    → {@code bodyText}</li>
     *   <li>{@code engine}     → {@code sourceTier} + {@code sourceName}</li>
     *   <li>{@code retrievedAt}→ {@code publishedAt}</li>
     *   <li>topic              → the original research {@code query}</li>
     *   <li>sourceWeight       → 0.85 for PERPLEXITY, 0.6 for GOOGLE</li>
     * </ul>
     */
    private List<NewsArticle> toNewsArticles(List<RetrievedSource> sources, String query) {
        log.debug("toNewsArticles() | sources={}, query={}", sources.size(), query);

        List<NewsArticle> result = new ArrayList<>();
        for (RetrievedSource source : sources) {
            URI uri;
            try {
                uri = (source.url() != null && !source.url().isBlank())
                        ? URI.create(source.url())
                        : URI.create("");
            } catch (IllegalArgumentException ex) {
                log.warn("toNewsArticles() | invalid URI for sourceId={}: {} — using empty URI",
                         source.sourceId(), source.url());
                uri = URI.create("");
            }

            double weight = "PERPLEXITY".equalsIgnoreCase(source.engine()) ? 0.85 : 0.6;

            result.add(new NewsArticle(
                    source.sourceId(),
                    source.title(),
                    uri,
                    source.snippet(),
                    query,
                    null,
                    1L,
                    source.engine(),
                    source.engine().toUpperCase(),
                    weight,
                    source.retrievedAt()));
        }

        log.debug("toNewsArticles() | return={} articles", result.size());
        return result;
    }
}
