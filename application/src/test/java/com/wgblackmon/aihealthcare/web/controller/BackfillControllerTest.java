package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed.BackfillProperties;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed.PubMedBackfillHarvester;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link BackfillController}.
 *
 * <p>Verifies the PubMed backfill REST endpoints return correct responses
 * and delegate to the harvester and storage port appropriately.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(BackfillController.class)
class BackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PubMedBackfillHarvester pubMedHarvester;

    @MockBean
    private ArticleStoragePort articleStoragePort;

    @MockBean
    private BackfillProperties backfillProperties;

    @MockBean
    private ApiKeyPort apiKeyPort;

    private NewsArticle buildTestArticle(String pmid) {
        return new NewsArticle(
                "pubmed-" + pmid,
                "Test Article " + pmid,
                URI.create("https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/"),
                "Abstract text for article " + pmid,
                "General AI Healthcare News",
                "Author A",
                null,
                "PubMed Backfill",
                "ACADEMIC",
                0.9,
                Instant.now()
        );
    }

    @Test
    void triggerBackfill_withDefaults_returnsResults() throws Exception {
        when(backfillProperties.getFromYear()).thenReturn(2022);
        when(backfillProperties.getToYear()).thenReturn(2025);
        when(backfillProperties.getMaxPerQuery()).thenReturn(50);
        when(backfillProperties.getQueries()).thenReturn(List.of(
                "\"AI\" AND \"healthcare\"",
                "\"AI\" AND \"legal\""
        ));

        List<NewsArticle> batch1 = List.of(buildTestArticle("111"), buildTestArticle("222"));
        List<NewsArticle> batch2 = List.of(buildTestArticle("333"));

        when(pubMedHarvester.harvest(eq("\"AI\" AND \"healthcare\""),
                any(LocalDate.class), any(LocalDate.class), eq(50)))
                .thenReturn(batch1);
        when(pubMedHarvester.harvest(eq("\"AI\" AND \"legal\""),
                any(LocalDate.class), any(LocalDate.class), eq(50)))
                .thenReturn(batch2);

        mockMvc.perform(post("/monitoring/backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queriesExecuted").value(2))
                .andExpect(jsonPath("$.totalArticles").value(3))
                .andExpect(jsonPath("$.queryResults[0].query").value("\"AI\" AND \"healthcare\""))
                .andExpect(jsonPath("$.queryResults[0].articlesFound").value(2))
                .andExpect(jsonPath("$.queryResults[1].articlesFound").value(1));

        verify(articleStoragePort).save(batch1);
        verify(articleStoragePort).save(batch2);
    }

    @Test
    void triggerBackfill_withCustomYears_overridesDefaults() throws Exception {
        when(backfillProperties.getFromYear()).thenReturn(2022);
        when(backfillProperties.getToYear()).thenReturn(2025);
        when(backfillProperties.getMaxPerQuery()).thenReturn(50);
        when(backfillProperties.getQueries()).thenReturn(List.of("\"AI\" AND \"healthcare\""));

        when(pubMedHarvester.harvest(anyString(),
                eq(LocalDate.of(2023, 1, 1)),
                eq(LocalDate.of(2024, 12, 31)),
                eq(10)))
                .thenReturn(List.of(buildTestArticle("555")));

        mockMvc.perform(post("/monitoring/backfill")
                        .param("fromYear", "2023")
                        .param("toYear", "2024")
                        .param("maxPerQuery", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArticles").value(1));
    }

    @Test
    void triggerBackfill_noArticlesFound_stillReturnsOk() throws Exception {
        when(backfillProperties.getFromYear()).thenReturn(2022);
        when(backfillProperties.getToYear()).thenReturn(2025);
        when(backfillProperties.getMaxPerQuery()).thenReturn(50);
        when(backfillProperties.getQueries()).thenReturn(List.of("\"nonexistent term\""));

        when(pubMedHarvester.harvest(anyString(), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(post("/monitoring/backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArticles").value(0))
                .andExpect(jsonPath("$.queryResults[0].articlesFound").value(0));

        verify(articleStoragePort, never()).save(any());
    }

    @Test
    void triggerCustomBackfill_withQuery_returnsResults() throws Exception {
        List<NewsArticle> articles = List.of(
                buildTestArticle("777"), buildTestArticle("888"));

        when(pubMedHarvester.harvest(eq("custom query"),
                eq(LocalDate.of(2023, 1, 1)),
                eq(LocalDate.of(2024, 12, 31)),
                eq(25)))
                .thenReturn(articles);

        mockMvc.perform(post("/monitoring/backfill/custom")
                        .param("query", "custom query")
                        .param("fromYear", "2023")
                        .param("toYear", "2024")
                        .param("maxResults", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queriesExecuted").value(1))
                .andExpect(jsonPath("$.totalArticles").value(2))
                .andExpect(jsonPath("$.queryResults[0].query").value("custom query"));

        verify(articleStoragePort).save(articles);
    }

    @Test
    void triggerCustomBackfill_defaultYears_usesDefaults() throws Exception {
        when(pubMedHarvester.harvest(eq("my query"),
                eq(LocalDate.of(2022, 1, 1)),
                eq(LocalDate.of(2025, 12, 31)),
                eq(100)))
                .thenReturn(List.of());

        mockMvc.perform(post("/monitoring/backfill/custom")
                        .param("query", "my query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArticles").value(0));
    }
}
