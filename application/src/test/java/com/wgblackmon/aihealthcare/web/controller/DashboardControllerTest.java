package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.VariantScore;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link DashboardController}.
 *
 * <p>Covers {@code GET /dashboard} (main analytics view),
 * {@code GET /dashboard/articles} (per-topic article detail with sort),
 * and {@code GET /dashboard/news} (all topics grouped with section headers).
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-05-04
 * @updated 2026-05-15
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAnalyticsUseCase analyticsUseCase;

    @MockitoBean
    private ArticleIngestionPort articleIngestionPort;

    @MockitoBean
    private NewsTopicProperties newsTopicProperties;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private IngestionAnalytics sampleIngestion() {
        return new IngestionAnalytics(
                200L,
                List.of(new CountByLabel("ACADEMIC", 120L), new CountByLabel("INDUSTRY", 80L)),
                List.of(new CountByLabel("PubMed AI Healthcare", 120L)),
                18L, 65L);
    }

    private RunAnalytics sampleRuns() {
        return new RunAnalytics(10L, 3L, 6L, 1L, Instant.parse("2026-04-30T10:00:00Z"));
    }

    private RunAnalytics emptyRuns() {
        return new RunAnalytics(0L, 0L, 0L, 0L, null);
    }

    private EvaluationAnalytics sampleEvaluations() {
        VariantScore vs = new VariantScore("v1", "Baseline", 8L, 0.85, 0.80, 0.88, 0.82, 0.86, 0.87);
        return new EvaluationAnalytics(8L, 3L, List.of(vs), "v1");
    }

    private EvaluationAnalytics emptyEvaluations() {
        return new EvaluationAnalytics(0L, 0L, List.of(), null);
    }

    private NewsArticle sampleArticle(String title, Instant publishedAt) {
        return new NewsArticle(
                "id-1", title, URI.create("https://example.com/article"),
                "body text", "PubMed AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, publishedAt);
    }

    // -------------------------------------------------------------------------
    // GET /dashboard
    // -------------------------------------------------------------------------

    @Test
    void dashboard_returns200AndDashboardView() throws Exception {
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(sampleIngestion());
        when(analyticsUseCase.getRunAnalytics()).thenReturn(sampleRuns());
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(sampleEvaluations());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    void dashboard_modelContainsAllThreeAnalyticsAttributes() throws Exception {
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(sampleIngestion());
        when(analyticsUseCase.getRunAnalytics()).thenReturn(sampleRuns());
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(sampleEvaluations());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("ingestion", "runs", "evaluations", "mostRecentRunDisplay"));
    }

    @Test
    void dashboard_rendersIngestionTotals() throws Exception {
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(sampleIngestion());
        when(analyticsUseCase.getRunAnalytics()).thenReturn(sampleRuns());
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(sampleEvaluations());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("200")))
                .andExpect(content().string(containsString("18")))
                .andExpect(content().string(containsString("ACADEMIC")));
    }

    @Test
    void dashboard_rendersRunTimestamp() throws Exception {
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(sampleIngestion());
        when(analyticsUseCase.getRunAnalytics()).thenReturn(sampleRuns());
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(sampleEvaluations());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-04-30 10:00 UTC")));
    }

    @Test
    void dashboard_nullMostRecentRun_displaysEmptyDash() throws Exception {
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(sampleIngestion());
        when(analyticsUseCase.getRunAnalytics()).thenReturn(emptyRuns());
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(emptyEvaluations());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("mostRecentRunDisplay", (Object) null));
    }

    @Test
    void dashboard_noEvaluations_rendersEmptyState() throws Exception {
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(sampleIngestion());
        when(analyticsUseCase.getRunAnalytics()).thenReturn(emptyRuns());
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(emptyEvaluations());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No evaluation data yet")));
    }

    @Test
    void dashboard_withEvaluations_rendersBestVariantBadge() throws Exception {
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(sampleIngestion());
        when(analyticsUseCase.getRunAnalytics()).thenReturn(sampleRuns());
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(sampleEvaluations());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Best")))
                .andExpect(content().string(containsString("Baseline")));
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/articles
    // -------------------------------------------------------------------------

    @Test
    void articles_returns200AndArticlesView() throws Exception {
        NewsArticle article = sampleArticle("AI Advances in Radiology", Instant.parse("2026-05-01T09:00:00Z"));
        when(articleIngestionPort.fetchAllByTopic(eq("PubMed AI Healthcare")))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/dashboard/articles").param("topic", "PubMed AI Healthcare"))
                .andExpect(status().isOk())
                .andExpect(view().name("articles"));
    }

    @Test
    void articles_rendersTopicAndArticleTitle() throws Exception {
        NewsArticle article = sampleArticle("AI Advances in Radiology", Instant.parse("2026-05-01T09:00:00Z"));
        when(articleIngestionPort.fetchAllByTopic(eq("PubMed AI Healthcare")))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/dashboard/articles").param("topic", "PubMed AI Healthcare"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PubMed AI Healthcare")))
                .andExpect(content().string(containsString("AI Advances in Radiology")));
    }

    @Test
    void articles_defaultSortIsDesc() throws Exception {
        when(articleIngestionPort.fetchAllByTopic(eq("PubMed AI Healthcare")))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/articles").param("topic", "PubMed AI Healthcare"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "desc"));
    }

    @Test
    void articles_ascSortParamPassedToModel() throws Exception {
        when(articleIngestionPort.fetchAllByTopic(eq("PubMed AI Healthcare")))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/articles")
                        .param("topic", "PubMed AI Healthcare")
                        .param("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "asc"));
    }

    @Test
    void articles_noResults_rendersEmptyState() throws Exception {
        when(articleIngestionPort.fetchAllByTopic(eq("PubMed AI Healthcare")))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/articles").param("topic", "PubMed AI Healthcare"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No articles found")));
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/news
    // -------------------------------------------------------------------------

    @Test
    void news_returns200AndNewsListingView() throws Exception {
        when(newsTopicProperties.getTopics()).thenReturn(
                List.of("General AI Healthcare News", "Anthropic Healthcare"));
        when(articleIngestionPort.fetchAllByTopic(eq("General AI Healthcare News")))
                .thenReturn(List.of());
        when(articleIngestionPort.fetchAllByTopic(eq("Anthropic Healthcare")))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(view().name("news-listing"));
    }

    @Test
    void news_rendersTopicHeadersWithArticles() throws Exception {
        NewsArticle a1 = sampleArticle("AI Article", Instant.parse("2026-05-15T10:00:00Z"));
        NewsArticle a2 = sampleArticle("Anthropic Article", Instant.parse("2026-05-15T09:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(
                List.of("General AI Healthcare News", "Anthropic Healthcare", "Beckers Hospital Review"));
        when(articleIngestionPort.fetchAllByTopic(eq("General AI Healthcare News")))
                .thenReturn(List.of(a1));
        when(articleIngestionPort.fetchAllByTopic(eq("Anthropic Healthcare")))
                .thenReturn(List.of(a2));
        when(articleIngestionPort.fetchAllByTopic(eq("Beckers Hospital Review")))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("General AI Healthcare News")))
                .andExpect(content().string(containsString("Anthropic Healthcare")));
    }

    @Test
    void news_hidesEmptyTopicSections() throws Exception {
        when(newsTopicProperties.getTopics()).thenReturn(
                List.of("General AI Healthcare News", "Empty Topic"));
        when(articleIngestionPort.fetchAllByTopic(eq("General AI Healthcare News")))
                .thenReturn(List.of(sampleArticle("Some Article", Instant.now())));
        when(articleIngestionPort.fetchAllByTopic(eq("Empty Topic")))
                .thenReturn(List.of());

        String html = mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assert !html.contains("Empty Topic") : "Empty topic section should be hidden";
    }

    @Test
    void news_rendersArticlesUnderTopic() throws Exception {
        NewsArticle article = sampleArticle("Mythos in Healthcare", Instant.parse("2026-05-15T10:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchAllByTopic(eq("General AI Healthcare News")))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mythos in Healthcare")));
    }

    @Test
    void news_modelContainsTotalArticleCount() throws Exception {
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchAllByTopic(eq("General AI Healthcare News")))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("topicArticles", "topicNames", "totalArticles"));
    }
}
