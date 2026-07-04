package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link ArticleSearchController}.
 *
 * <p>Covers {@code GET /api/v1/articles/search} with optional filter
 * parameters mapped to {@link ArticleSearchCriteria}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-06-01
 * @updated 2026-06-06
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(ArticleSearchController.class)
class ArticleSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private SearchArticlesUseCase searchUseCase;

    private NewsArticle sampleArticle(String title) {
        return new NewsArticle(
                "id-1", title, URI.create("https://example.com"),
                "body", "PubMed AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9,
                Instant.parse("2026-05-01T09:00:00Z"));
    }

    @Test
    void search_noParams_returns200EmptyList() throws Exception {
        when(searchUseCase.search(any(ArticleSearchCriteria.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/articles/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void search_withTitleParam_passesCriteriaToUseCase() throws Exception {
        when(searchUseCase.search(any(ArticleSearchCriteria.class)))
                .thenReturn(List.of(sampleArticle("AI in Radiology")));

        mockMvc.perform(get("/api/v1/articles/search").param("title", "radiology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("AI in Radiology"));

        ArgumentCaptor<ArticleSearchCriteria> captor =
                ArgumentCaptor.forClass(ArticleSearchCriteria.class);
        verify(searchUseCase).search(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("radiology");
        assertThat(captor.getValue().topic()).isNull();
    }

    @Test
    void search_withMultipleParams_passesCombinedCriteria() throws Exception {
        when(searchUseCase.search(any(ArticleSearchCriteria.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/articles/search")
                        .param("title", "AI")
                        .param("topic", "PubMed")
                        .param("sourceName", "PubMed"))
                .andExpect(status().isOk());

        ArgumentCaptor<ArticleSearchCriteria> captor =
                ArgumentCaptor.forClass(ArticleSearchCriteria.class);
        verify(searchUseCase).search(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("AI");
        assertThat(captor.getValue().topic()).isEqualTo("PubMed");
        assertThat(captor.getValue().sourceName()).isEqualTo("PubMed");
    }

    @Test
    void search_responseContainsExpectedFields() throws Exception {
        NewsArticle article = sampleArticle("AI Radiology Breakthrough");
        when(searchUseCase.search(any(ArticleSearchCriteria.class)))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/api/v1/articles/search").param("title", "radiology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].articleId").value("id-1"))
                .andExpect(jsonPath("$[0].title").value("AI Radiology Breakthrough"))
                .andExpect(jsonPath("$[0].topic").value("PubMed AI Healthcare"))
                .andExpect(jsonPath("$[0].author").value("Dr. Smith"))
                .andExpect(jsonPath("$[0].sourceName").value("PubMed"));
    }

    @Test
    void search_multipleResults_allReturned() throws Exception {
        NewsArticle a1 = sampleArticle("Article One");
        NewsArticle a2 = new NewsArticle(
                "id-2", "Article Two", URI.create("https://example.com/2"),
                "body2", "Anthropic Healthcare", null,
                2L, "Anthropic", "COMPETITOR", 0.7,
                Instant.parse("2026-05-02T09:00:00Z"));
        when(searchUseCase.search(any(ArticleSearchCriteria.class)))
                .thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/api/v1/articles/search").param("topic", "Healthcare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Article One"))
                .andExpect(jsonPath("$[1].title").value("Article Two"));
    }
}
