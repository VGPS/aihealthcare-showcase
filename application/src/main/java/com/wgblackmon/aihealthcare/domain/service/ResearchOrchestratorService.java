package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchPlan;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.model.RetrievalQuery;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Application-layer service that implements {@link ConductResearchUseCase}.
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
 * </ol>
 *
 * <p><b>STAGED_RESEARCH path</b> (requires AI + optional Perplexity API key):
 * <ol>
 *   <li>{@link ResearchPlanningService#plan} decomposes the query into sub-queries.</li>
 *   <li>Each sub-query is dispatched to {@code perplexityAdapter.retrieve()}.</li>
 *   <li>All sources are pooled and assembled into citations by {@link CitationAssembler}.</li>
 *   <li>{@link ResearchSynthesisService#synthesize} calls the AI to produce structured sections.</li>
 * </ol>
 *
 * <p>The effective mode is: {@code request.mode()} if non-null, else {@code defaultMode}.
 *
 * <p>This class is not annotated with {@code @Service} — it is wired as a bean
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@Slf4j
public class ResearchOrchestratorService implements ConductResearchUseCase {

    private final ResearchMode            defaultMode;
    private final SourceRetrievalPort     legacyAdapter;
    private final SourceRetrievalPort     perplexityAdapter;
    private final ResearchPlanningService planningService;
    private final ResearchSynthesisService synthesisService;
    private final CitationAssembler       citationAssembler;

    /**
     * Constructs the orchestrator with all pipeline dependencies.
     *
     * @param defaultMode        Configured default when the request carries a {@code null} mode.
     * @param legacyAdapter      {@link SourceRetrievalPort} backed by stored article ingestion.
     * @param perplexityAdapter  {@link SourceRetrievalPort} backed by Perplexity Sonar.
     * @param planningService    AI-based query decomposition service.
     * @param synthesisService   AI-based source synthesis service.
     * @param citationAssembler  Pure-Java citation deduplication and numbering service.
     */
    public ResearchOrchestratorService(ResearchMode defaultMode,
                                       SourceRetrievalPort legacyAdapter,
                                       SourceRetrievalPort perplexityAdapter,
                                       ResearchPlanningService planningService,
                                       ResearchSynthesisService synthesisService,
                                       CitationAssembler citationAssembler) {
        log.debug("ResearchOrchestratorService() | defaultMode={}, legacyAdapter={}, "
                  + "perplexityAdapter={}, planningService={}, synthesisService={}",
                  defaultMode,
                  legacyAdapter.getClass().getSimpleName(),
                  perplexityAdapter.getClass().getSimpleName(),
                  planningService.getClass().getSimpleName(),
                  synthesisService.getClass().getSimpleName());
        this.defaultMode       = defaultMode;
        this.legacyAdapter     = legacyAdapter;
        this.perplexityAdapter = perplexityAdapter;
        this.planningService   = planningService;
        this.synthesisService  = synthesisService;
        this.citationAssembler = citationAssembler;
        log.debug("ResearchOrchestratorService() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Routes to the LEGACY_GOOGLE or STAGED_RESEARCH branch based on the effective mode.
     */
    @Override
    public ResearchAnswer conduct(ResearchRequest request) {
        log.debug("conduct() | request={}", request);

        ResearchMode effectiveMode = request.mode() != null ? request.mode() : defaultMode;
        log.info("conduct() | effectiveMode={}, query='{}'", effectiveMode, request.query());

        ResearchAnswer result;
        if (effectiveMode == ResearchMode.STAGED_RESEARCH) {
            result = conductStaged(request);
        } else {
            result = conductLegacy(request);
        }

        log.info("conduct() | answer assembled: answerId={}, sections={}, citations={}",
                 result.answerId(), result.sections().size(), result.allCitations().size());
        log.debug("conduct() | return={}", result.answerId());
        return result;
    }

    // -------------------------------------------------------------------------
    // Pipeline branches
    // -------------------------------------------------------------------------

    /**
     * LEGACY_GOOGLE pipeline: fetch stored articles, assemble into a single-section answer.
     * No AI planning or synthesis is performed.
     */
    private ResearchAnswer conductLegacy(ResearchRequest request) {
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
        return result;
    }

    /**
     * STAGED_RESEARCH pipeline: plan → retrieve → synthesize → cite.
     */
    private ResearchAnswer conductStaged(ResearchRequest request) {
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
        return result;
    }

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
}
