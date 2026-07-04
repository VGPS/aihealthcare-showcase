package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link AiSearchRestController}.
 *
 * <p>Covers the {@code GET /api/v1/search/ai} REST endpoint — successful searches,
 * empty results, missing query parameter, and authentication requirements.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-02
 */
@Import(SecurityConfig.class)
@WebMvcTest(AiSearchRestController.class)
class AiSearchRestControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private ConductAiSearchUseCase aiSearchUseCase;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private NewsArticle sampleArticle(String id, String title) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, "AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.parse("2026-05-15T10:00:00Z"));
    }

    // -------------------------------------------------------------------------
    // Successful search
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void aiSearch_withQuery_returns200WithSyntheses() throws Exception {
        NewsArticle a1 = sampleArticle("a1", "AI Diagnostics");
        AiSearchSynthesis claude = new AiSearchSynthesis(
                "Claude", "Claude summary", List.of("Finding 1", "Finding 2"), Instant.now());
        AiSearchSynthesis gpt = new AiSearchSynthesis(
                "GPT", "GPT summary", List.of("Finding 3"), Instant.now());
        AiSearchSynthesis perplexity = new AiSearchSynthesis(
                "Perplexity", "Perplexity summary", List.of("Finding 4", "Finding 5"), Instant.now());
        AiSearchResult result = new AiSearchResult(
                "search-1", "AI diagnostics", List.of(a1), List.of(claude, gpt, perplexity), Instant.now());

        when(aiSearchUseCase.search(eq("AI diagnostics"), eq(10), isNull())).thenReturn(result);

        mockMvc.perform(get("/api/v1/search/ai").param("q", "AI diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchId", is("search-1")))
                .andExpect(jsonPath("$.query", is("AI diagnostics")))
                .andExpect(jsonPath("$.articleCount", is(1)))
                .andExpect(jsonPath("$.syntheses", hasSize(3)))
                .andExpect(jsonPath("$.syntheses[0].modelName", is("Claude")))
                .andExpect(jsonPath("$.syntheses[0].summary", is("Claude summary")))
                .andExpect(jsonPath("$.syntheses[0].keyFindings", hasSize(2)))
                .andExpect(jsonPath("$.syntheses[1].modelName", is("GPT")))
                .andExpect(jsonPath("$.syntheses[2].modelName", is("Perplexity")))
                .andExpect(jsonPath("$.syntheses[2].keyFindings", hasSize(2)));
    }

    // -------------------------------------------------------------------------
    // Empty results
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void aiSearch_noArticlesFound_returns200WithEmptySyntheses() throws Exception {
        AiSearchResult empty = new AiSearchResult(
                "search-2", "obscure", List.of(), List.of(), Instant.now());
        when(aiSearchUseCase.search(eq("obscure"), eq(10), isNull())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/search/ai").param("q", "obscure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleCount", is(0)))
                .andExpect(jsonPath("$.syntheses", hasSize(0)));
    }

    // -------------------------------------------------------------------------
    // Missing query — 400
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void aiSearch_missingQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/search/ai"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Unauthenticated — API is permitAll
    // -------------------------------------------------------------------------

    @Test
    void aiSearch_noAuth_stillPermitted() throws Exception {
        AiSearchResult result = new AiSearchResult(
                "s3", "test", List.of(), List.of(), Instant.now());
        when(aiSearchUseCase.search(eq("test"), eq(10), isNull())).thenReturn(result);

        mockMvc.perform(get("/api/v1/search/ai").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query", is("test")))
                .andExpect(jsonPath("$.articleCount", is(0)));
    }
}
