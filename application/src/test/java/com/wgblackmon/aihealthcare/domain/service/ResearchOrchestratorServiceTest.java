package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchPlan;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.RetrievalQuery;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchExportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResearchOrchestratorService}.
 *
 * <p>All AI, retrieval, and persistence ports are mocked — no real calls.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-05-04
 * @updated 2026-05-06
 */
@ExtendWith(MockitoExtension.class)
class ResearchOrchestratorServiceTest {

    @Mock private SourceRetrievalPort     legacyAdapter;
    @Mock private SourceRetrievalPort     perplexityAdapter;
    @Mock private ResearchPlanningService planningService;
    @Mock private ResearchSynthesisService synthesisService;
    @Mock private ArticleStoragePort      articleStoragePort;
    @Mock private ResearchRunPort         researchRunPort;
    @Mock private ResearchExportPort      researchExportPort;

    private CitationAssembler           citationAssembler;
    private ResearchOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        citationAssembler = new CitationAssembler();
        orchestrator = new ResearchOrchestratorService(
                ResearchMode.LEGACY_GOOGLE,
                legacyAdapter,
                perplexityAdapter,
                planningService,
                synthesisService,
                citationAssembler,
                articleStoragePort,
                researchRunPort,
                researchExportPort);
    }

    // -------------------------------------------------------------------------
    // LEGACY_GOOGLE routing
    // -------------------------------------------------------------------------

    @Test
    void conduct_legacyMode_usesLegacyAdapterOnly() {
        when(legacyAdapter.retrieve(any(RetrievalQuery.class)))
                .thenReturn(List.of(source("id-1", "AI Diagnostics Article", "https://example.com/1")));

        ResearchRequest request = new ResearchRequest("AI diagnostics", ResearchMode.LEGACY_GOOGLE, null, 20);
        ResearchAnswer answer = orchestrator.conduct(request);

        assertThat(answer).isNotNull();
        assertThat(answer.sections()).isNotEmpty();
        verify(legacyAdapter).retrieve(any(RetrievalQuery.class));
        verify(perplexityAdapter, never()).retrieve(any());
        verify(planningService, never()).plan(anyString(), anyString());
    }

    @Test
    void conduct_legacyMode_answerContainsQuery() {
        when(legacyAdapter.retrieve(any())).thenReturn(Collections.emptyList());

        ResearchRequest request = new ResearchRequest("AI in surgery", ResearchMode.LEGACY_GOOGLE, null, 10);
        ResearchAnswer answer = orchestrator.conduct(request);

        assertThat(answer.query()).isEqualTo("AI in surgery");
    }

    @Test
    void conduct_legacyMode_answerHasNonNullId() {
        when(legacyAdapter.retrieve(any())).thenReturn(Collections.emptyList());

        ResearchAnswer answer = orchestrator.conduct(
                new ResearchRequest("query", ResearchMode.LEGACY_GOOGLE, null, 5));

        assertThat(answer.answerId()).isNotBlank();
    }

    @Test
    void conduct_legacyMode_sourcesBecomeCitations() {
        when(legacyAdapter.retrieve(any())).thenReturn(List.of(
                source("a", "Article A", "https://x.com/a"),
                source("b", "Article B", "https://x.com/b")));

        ResearchAnswer answer = orchestrator.conduct(
                new ResearchRequest("test", ResearchMode.LEGACY_GOOGLE, null, 20));

        assertThat(answer.allCitations()).hasSize(2);
        assertThat(answer.allCitations().get(0).citationNumber()).isEqualTo(1);
        assertThat(answer.allCitations().get(1).citationNumber()).isEqualTo(2);
    }

    @Test
    void conduct_legacyMode_doesNotPersistArticlesOrExport() {
        when(legacyAdapter.retrieve(any())).thenReturn(List.of(
                source("id-1", "Article", "https://example.com/1")));

        orchestrator.conduct(new ResearchRequest("AI", ResearchMode.LEGACY_GOOGLE, null, 10));

        // LEGACY_GOOGLE sources are already in the DB — no re-save or export
        verify(articleStoragePort, never()).save(anyList());
        verify(researchExportPort, never()).export(anyString(), anyList());
    }

    @Test
    void conduct_legacyMode_alwaysPersistsResearchRun() {
        when(legacyAdapter.retrieve(any())).thenReturn(Collections.emptyList());

        orchestrator.conduct(new ResearchRequest("AI", ResearchMode.LEGACY_GOOGLE, null, 10));

        verify(researchRunPort).save(any());
    }

    // -------------------------------------------------------------------------
    // STAGED_RESEARCH routing
    // -------------------------------------------------------------------------

    @Test
    void conduct_stagedMode_callsPlanningService() {
        ResearchPlan plan = new ResearchPlan("plan-1", "AI in cancer",
                List.of("AI cancer diagnostics", "machine learning oncology"),
                "Decomposed into diagnostics and oncology sub-queries", 20);
        when(planningService.plan(anyString(), nullable(String.class))).thenReturn(plan);
        when(perplexityAdapter.retrieve(any())).thenReturn(Collections.emptyList());
        when(legacyAdapter.retrieve(any())).thenReturn(Collections.emptyList());
        when(synthesisService.synthesize(anyString(), any(), any(), any()))
                .thenReturn(emptyAnswer("AI in cancer"));

        ResearchRequest request = new ResearchRequest("AI in cancer", ResearchMode.STAGED_RESEARCH, null, 20);
        orchestrator.conduct(request);

        verify(planningService).plan("AI in cancer", null);
    }

    @Test
    void conduct_stagedMode_perplexityEmptyFallsBackToLegacy() {
        ResearchPlan plan = new ResearchPlan("plan-1", "AI query",
                List.of("sub-query"), "rationale", 10);
        when(planningService.plan(anyString(), nullable(String.class))).thenReturn(plan);
        when(perplexityAdapter.retrieve(any())).thenReturn(Collections.emptyList());
        when(legacyAdapter.retrieve(any())).thenReturn(List.of(
                source("id-1", "Fallback Article", "https://fallback.com")));
        when(synthesisService.synthesize(anyString(), any(), any(), any()))
                .thenReturn(emptyAnswer("AI query"));

        ResearchRequest request = new ResearchRequest("AI query", ResearchMode.STAGED_RESEARCH, null, 20);
        orchestrator.conduct(request);

        verify(legacyAdapter).retrieve(any(RetrievalQuery.class));
    }

    @Test
    void conduct_stagedMode_withSources_persistsArticlesAndExports() {
        ResearchPlan plan = new ResearchPlan("plan-1", "AI health",
                List.of("AI health query"), "rationale", 10);
        when(planningService.plan(anyString(), nullable(String.class))).thenReturn(plan);
        when(perplexityAdapter.retrieve(any())).thenReturn(List.of(
                source("p1", "Perplexity Article", "https://perplexity.com/1")));
        when(synthesisService.synthesize(anyString(), any(), any(), any()))
                .thenReturn(emptyAnswer("AI health"));

        orchestrator.conduct(new ResearchRequest("AI health", ResearchMode.STAGED_RESEARCH, null, 10));

        verify(articleStoragePort, atLeastOnce()).save(anyList());
        verify(researchExportPort, atLeastOnce()).export(anyString(), anyList());
        verify(researchRunPort).save(any());
    }

    // -------------------------------------------------------------------------
    // Default mode fallback
    // -------------------------------------------------------------------------

    @Test
    void conduct_nullModeInRequest_usesConfiguredDefault() {
        when(legacyAdapter.retrieve(any())).thenReturn(Collections.emptyList());

        // Orchestrator was constructed with LEGACY_GOOGLE as default
        ResearchRequest request = new ResearchRequest("query", null, null, 10);
        ResearchAnswer answer = orchestrator.conduct(request);

        assertThat(answer).isNotNull();
        verify(legacyAdapter).retrieve(any());
        verify(planningService, never()).plan(anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RetrievedSource source(String id, String title, String url) {
        return new RetrievedSource(id, title, url, "snippet", "GOOGLE", Instant.now());
    }

    private ResearchAnswer emptyAnswer(String query) {
        return new com.wgblackmon.aihealthcare.domain.model.ResearchAnswer(
                "answer-id",
                query,
                List.of(new com.wgblackmon.aihealthcare.domain.model.ResearchSection(
                        "Research Findings", "No sources.", Collections.emptyList())),
                Collections.emptyList(),
                Instant.now());
    }
}
