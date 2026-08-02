package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link SearchDiagnosticController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-01
 * @updated 2026-08-01
 */
@WebMvcTest(SearchDiagnosticController.class)
class SearchDiagnosticControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrendSnapshotPort trendSnapshotPort;

    @MockBean
    private ArticleSearchPort articleSearchPort;

    @MockBean
    private SearchArticlesUseCase searchArticlesUseCase;

    @Test
    @WithMockUser
    void diagnose_returnsAllThreeResults() throws Exception {
        // Snapshot with one matching article
        ScoredArticle scored = new ScoredArticle("art-1", "Mayo Clinic AI tool frees up 11 minutes per patient",
                8, "Significant clinical deployment", "AI Ambient Clinical Documentation",
                "https://example.com/mayo", "Beckers", Instant.now());
        TrendSignal signal = new TrendSignal("ai ambient clinical documentation", 5, 2, 0,
                2.5, TrendDirection.RISING, Instant.now(), List.of(scored), null);
        TrendSnapshot snapshot = new TrendSnapshot(Instant.now(), 30, List.of(signal), List.of(), List.of(), 10);
        when(trendSnapshotPort.findLatest()).thenReturn(Optional.of(snapshot));

        // Semantic search returns the article
        NewsArticle article = new NewsArticle("art-1", "Mayo Clinic AI tool frees up 11 minutes per patient",
                URI.create("https://example.com/mayo"), "body", "Beckers", null, null, "Beckers", "INDUSTRY", 0.8, Instant.now());
        when(articleSearchPort.findSimilar(any(), anyInt())).thenReturn(List.of(article));

        // Text search returns the article
        when(searchArticlesUseCase.search(any(ArticleSearchCriteria.class))).thenReturn(List.of(article));

        mockMvc.perform(get("/api/v1/search/diagnostic")
                        .param("query", "Mayo Clinic AI tool frees up 11 minutes per patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query", is("Mayo Clinic AI tool frees up 11 minutes per patient")))
                .andExpect(jsonPath("$.trendSnapshotLookup.found", is(true)))
                .andExpect(jsonPath("$.trendSnapshotLookup.matches", hasSize(1)))
                .andExpect(jsonPath("$.semanticSearch.resultCount", is(1)))
                .andExpect(jsonPath("$.semanticSearch.results[0].title", is("Mayo Clinic AI tool frees up 11 minutes per patient")))
                .andExpect(jsonPath("$.textSearch.resultCount", is(1)));
    }

    @Test
    @WithMockUser
    void diagnose_noSnapshot_returnsEmptySnapshotResult() throws Exception {
        when(trendSnapshotPort.findLatest()).thenReturn(Optional.empty());
        when(articleSearchPort.findSimilar(any(), anyInt())).thenReturn(List.of());
        when(searchArticlesUseCase.search(any(ArticleSearchCriteria.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search/diagnostic")
                        .param("query", "nonexistent article"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trendSnapshotLookup.found", is(false)))
                .andExpect(jsonPath("$.semanticSearch.resultCount", is(0)))
                .andExpect(jsonPath("$.textSearch.resultCount", is(0)));
    }

    @Test
    @WithMockUser
    void diagnose_customTopK_passedToSemanticSearch() throws Exception {
        when(trendSnapshotPort.findLatest()).thenReturn(Optional.empty());
        when(articleSearchPort.findSimilar(any(), eq(20))).thenReturn(List.of());
        when(searchArticlesUseCase.search(any(ArticleSearchCriteria.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search/diagnostic")
                        .param("query", "test")
                        .param("topK", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topK", is(20)));
    }

    @Test
    @WithMockUser
    void diagnose_missingQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/search/diagnostic"))
                .andExpect(status().isBadRequest());
    }
}
