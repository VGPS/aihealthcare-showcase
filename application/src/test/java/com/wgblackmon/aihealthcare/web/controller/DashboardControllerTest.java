package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.VariantScore;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
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
 * <p>Covers {@code GET /dashboard} (main analytics view) and
 * {@code GET /dashboard/articles} (per-topic article detail with sort).
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAnalyticsUseCase analyticsUseCase;

    @MockitoBean
    private ArticleIngestionPort articleIngestionPort;

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
}
