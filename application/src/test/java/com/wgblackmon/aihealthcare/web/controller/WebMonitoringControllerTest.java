package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.service.CompanyProfileService;
import com.wgblackmon.aihealthcare.domain.service.PerplexityCompanyDiscoveryService;
import com.wgblackmon.aihealthcare.domain.service.TopicSummaryGenerationService;
import com.wgblackmon.aihealthcare.infrastructure.ai.EmbeddingScheduler;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface.HuggingFaceHarvester;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.web.WebPageHarvester;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.PageContentHashEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.PageContentHashRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link WebMonitoringController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-05-22
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(WebMonitoringController.class)
class WebMonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ApiKeyPort apiKeyPort;

    @MockBean
    private WebPageHarvester webPageHarvester;

    @MockBean
    private HuggingFaceHarvester huggingFaceHarvester;

    @MockBean
    private ArticleStoragePort articleStoragePort;

    @MockBean
    private PageContentHashRepository hashRepository;

    @MockBean
    private ArticleHarvestingPort articleHarvestingPort;

    @MockBean
    private TopicSummaryGenerationService topicSummaryService;

    @MockBean
    private NewsTopicProperties newsTopicProperties;

    @MockBean
    private EmbeddingScheduler embeddingScheduler;

    @MockBean
    private PerplexityCompanyDiscoveryService companyDiscoveryService;

    @MockBean
    private DiscoverCompaniesUseCase discoverCompaniesUseCase;

    @MockBean
    private CompanyProfilePort companyProfilePort;

    @MockBean
    private CompanyProfileService companyProfileService;

    @MockBean
    private NewsArticleRepository newsArticleRepository;

    @MockBean
    private AnalyzeCompanySentimentUseCase sentimentUseCase;

    @Test
    void triggerCompetitorHarvest_withChanges_returns200() throws Exception {
        NewsArticle article = new NewsArticle(
                "test-id", "Changed Page", URI.create("https://example.com#snapshot-123"),
                "new content", "Test", "Test", 1L, "Test", "COMPETITOR", 0.7, Instant.now());
        when(webPageHarvester.harvestChangedPages()).thenReturn(List.of(article));
        when(hashRepository.count()).thenReturn(4L);

        mockMvc.perform(post("/api/v1/monitoring/harvest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changesDetected").value(1));

        verify(articleStoragePort).save(List.of(article));
    }

    @Test
    void triggerCompetitorHarvest_noChanges_returns200() throws Exception {
        when(webPageHarvester.harvestChangedPages()).thenReturn(List.of());
        when(hashRepository.count()).thenReturn(4L);

        mockMvc.perform(post("/api/v1/monitoring/harvest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changesDetected").value(0));

        verify(articleStoragePort, never()).save(anyList());
    }

    @Test
    void triggerHuggingFaceHarvest_withModels_returns200() throws Exception {
        NewsArticle model = new NewsArticle(
                "hf-test/model", "test/model", URI.create("https://huggingface.co/test/model"),
                "body", "HF LLMs", "test", 1L, "HF LLMs", "HUGGINGFACE", 0.5, Instant.now());
        when(huggingFaceHarvester.harvestModels()).thenReturn(List.of(model));

        mockMvc.perform(post("/api/v1/monitoring/huggingface"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelsDiscovered").value(1));

        verify(articleStoragePort).save(List.of(model));
    }

    @Test
    void triggerHuggingFaceHarvest_noModels_returns200() throws Exception {
        when(huggingFaceHarvester.harvestModels()).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/monitoring/huggingface"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelsDiscovered").value(0));

        verify(articleStoragePort, never()).save(anyList());
    }

    @Test
    void listPageHashes_returnsEntries() throws Exception {
        PageContentHashEntity entity = new PageContentHashEntity(
                "https://example.com/page", "abc123", Instant.parse("2026-04-19T10:00:00Z"));
        when(hashRepository.findAll()).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/v1/monitoring/hashes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pageUrl").value("https://example.com/page"))
                .andExpect(jsonPath("$[0].contentHash").value("abc123"));
    }

    @Test
    void listPageHashes_empty_returnsEmptyArray() throws Exception {
        when(hashRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/monitoring/hashes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void triggerTopicSummaries_returns200() throws Exception {
        when(newsTopicProperties.getTopics()).thenReturn(List.of("AI Healthcare", "OpenAI Healthcare"));

        mockMvc.perform(post("/api/v1/monitoring/summaries"))
                .andExpect(status().isOk());

        verify(topicSummaryService).generateSummaries(List.of("AI Healthcare", "OpenAI Healthcare"));
    }

    @Test
    void triggerFeedHarvest_withArticles_returns200() throws Exception {
        NewsArticle article = new NewsArticle(
                "feed-id-1", "RSS Article", URI.create("https://pubmed.ncbi.nlm.nih.gov/123"),
                "body text", "AI Healthcare", null, 1L, "PubMed AI Healthcare", "ACADEMIC", 0.9, Instant.now());
        when(articleHarvestingPort.harvestAll()).thenReturn(List.of(article));

        mockMvc.perform(post("/api/v1/monitoring/feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagesChecked").value(1))
                .andExpect(jsonPath("$.changesDetected").value(1));

        verify(articleStoragePort).save(List.of(article));
    }

    @Test
    void triggerFeedHarvest_noArticles_returns200() throws Exception {
        when(articleHarvestingPort.harvestAll()).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/monitoring/feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changesDetected").value(0));

        verify(articleStoragePort, never()).save(anyList());
    }

    @Test
    void triggerEmbedding_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/monitoring/embeddings"))
                .andExpect(status().isOk());

        verify(embeddingScheduler).embedArticles();
    }
}
