package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.VariantScore;
import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * @version 1.3
 * @since   2026-05-04
 * @updated 2026-05-30
 */
@Import(SecurityConfig.class)
@WithMockUser
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

    @MockitoBean
    private TopicSummaryPort topicSummaryPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private TierGatingService tierGatingService;

    @MockitoBean
    private SearchArticlesUseCase searchUseCase;

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
                .andExpect(content().string(containsString("PubMed AI Healthcare")));
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

    private void stubFreeTierGating() {
        when(subscriberPort.findByEmail(any())).thenReturn(Optional.empty());
        when(tierGatingService.archiveDaysFor(any())).thenReturn(7);
    }

    @Test
    void news_returns200AndNewsListingView() throws Exception {
        stubFreeTierGating();
        when(newsTopicProperties.getTopics()).thenReturn(
                List.of("General AI Healthcare News", "Anthropic Healthcare"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("Anthropic Healthcare"), eq(7)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(view().name("news-listing"));
    }

    @Test
    void news_rendersTopicHeadersWithArticles() throws Exception {
        stubFreeTierGating();
        NewsArticle a1 = sampleArticle("AI Article", Instant.parse("2026-05-15T10:00:00Z"));
        NewsArticle a2 = sampleArticle("Anthropic Article", Instant.parse("2026-05-15T09:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(
                List.of("General AI Healthcare News", "Anthropic Healthcare", "Beckers Hospital Review"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of(a1));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("Anthropic Healthcare"), eq(7)))
                .thenReturn(List.of(a2));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("Beckers Hospital Review"), eq(7)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("General AI Healthcare News")))
                .andExpect(content().string(containsString("Anthropic Healthcare")));
    }

    @Test
    void news_hidesEmptyTopicSections() throws Exception {
        stubFreeTierGating();
        when(newsTopicProperties.getTopics()).thenReturn(
                List.of("General AI Healthcare News", "Empty Topic"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of(sampleArticle("Some Article", Instant.now())));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("Empty Topic"), eq(7)))
                .thenReturn(List.of());

        String html = mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assert !html.contains("Empty Topic") : "Empty topic section should be hidden";
    }

    @Test
    void news_rendersArticlesUnderTopic() throws Exception {
        stubFreeTierGating();
        NewsArticle article = sampleArticle("Mythos in Healthcare", Instant.parse("2026-05-15T10:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mythos in Healthcare")));
    }

    @Test
    void news_modelContainsTotalArticleCount() throws Exception {
        stubFreeTierGating();
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("topicArticles", "topicNames", "totalArticles",
                        "articleTitles", "articlePublications"));
    }

    @Test
    void news_doesNotRenderAuthorOrSearchSourceColumns() throws Exception {
        stubFreeTierGating();
        NewsArticle article = sampleArticle("Some Article", Instant.parse("2026-05-15T10:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of(article));

        String html = mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assert !html.contains(">Author<") : "Author column should not appear";
        assert !html.contains("Search Source") : "Search Source column should not appear";
    }

    @Test
    void news_extractsPublicationFromTitle() throws Exception {
        stubFreeTierGating();
        NewsArticle article = new NewsArticle(
                "id-1", "AI in Nursing Practice - CancerNetwork",
                URI.create("https://example.com/article"),
                "body text", "General AI Healthcare News", null,
                1L, "Google News", "INDUSTRY", 0.6, Instant.parse("2026-05-15T10:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of(article));

        String html = mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articlePublications",
                        org.hamcrest.Matchers.hasEntry("id-1", "CancerNetwork")))
                .andReturn().getResponse().getContentAsString();

        assert html.contains("AI in Nursing Practice") : "Cleaned title should appear";
        assert html.contains("CancerNetwork") : "Publication should appear in its own column";
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/news — topic summaries
    // -------------------------------------------------------------------------

    @Test
    void news_rendersTopicSummaryWhenPresent() throws Exception {
        stubFreeTierGating();
        NewsArticle a1 = sampleArticle("Article One", Instant.parse("2026-05-15T10:00:00Z"));
        NewsArticle a2 = new NewsArticle(
                "id-2", "Article Two", URI.create("https://example.com/article-2"),
                "body text", "General AI Healthcare News", null,
                1L, "PubMed", "ACADEMIC", 0.9, Instant.parse("2026-05-15T09:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of(a1, a2));
        when(topicSummaryPort.findByTopic(eq("General AI Healthcare News")))
                .thenReturn(Optional.of(new TopicSummary(
                        "General AI Healthcare News",
                        "AI healthcare is advancing rapidly. New tools are emerging. Research continues.",
                        Instant.parse("2026-05-21T10:00:00Z"))));

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI healthcare is advancing rapidly")))
                .andExpect(content().string(containsString("AI Summary")));
    }

    @Test
    void news_noSummaryForSingleArticleTopic() throws Exception {
        stubFreeTierGating();
        when(newsTopicProperties.getTopics()).thenReturn(List.of("Anthropic Healthcare"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("Anthropic Healthcare"), eq(7)))
                .thenReturn(List.of(sampleArticle("Single Article", Instant.now())));

        String html = mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assert !html.contains("AI Summary") : "Summary should not appear for single-article topics";
    }

    @Test
    void news_modelContainsTopicSummariesAttribute() throws Exception {
        stubFreeTierGating();
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("topicSummaries"));
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/news — archive depth gating
    // -------------------------------------------------------------------------

    @Test
    void news_freeTier_showsArchiveBanner() throws Exception {
        stubFreeTierGating();
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(7)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("archiveLimited", true))
                .andExpect(model().attribute("archiveDays", 7))
                .andExpect(content().string(containsString("Upgrade to Member")));
    }

    @Test
    void news_memberTier_noArchiveBanner() throws Exception {
        Subscriber member = new Subscriber("user", "Member User", true, Instant.now(), SubscriptionTier.MEMBER);
        when(subscriberPort.findByEmail("user")).thenReturn(Optional.of(member));
        when(tierGatingService.archiveDaysFor(SubscriptionTier.MEMBER)).thenReturn(0);
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(0)))
                .thenReturn(List.of());

        String html = mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("archiveLimited", false))
                .andReturn().getResponse().getContentAsString();

        assert !html.contains("Upgrade to Member") : "Member should not see upgrade banner";
    }

    @Test
    void news_memberTier_usesUnlimitedArchive() throws Exception {
        Subscriber member = new Subscriber("user", "Member User", true, Instant.now(), SubscriptionTier.MEMBER);
        when(subscriberPort.findByEmail("user")).thenReturn(Optional.of(member));
        when(tierGatingService.archiveDaysFor(SubscriptionTier.MEMBER)).thenReturn(0);
        NewsArticle article = sampleArticle("Old Article", Instant.parse("2025-01-01T10:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(0)))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Old Article")));
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/search
    // -------------------------------------------------------------------------

    @Test
    void search_returns200AndSearchView() throws Exception {
        mockMvc.perform(get("/dashboard/search"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"));
    }

    @Test
    void search_noParams_modelHasSearchedFalse() throws Exception {
        mockMvc.perform(get("/dashboard/search"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searched", false))
                .andExpect(model().attribute("resultCount", 0));
    }

    @Test
    void search_withTitleParam_returnsMatchingArticles() throws Exception {
        NewsArticle article = sampleArticle("AI Radiology Breakthrough", Instant.parse("2026-05-01T09:00:00Z"));
        when(searchUseCase.search(any(ArticleSearchCriteria.class)))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/dashboard/search").param("title", "radiology"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("searched", true))
                .andExpect(model().attribute("resultCount", 1))
                .andExpect(content().string(containsString("AI Radiology Breakthrough")));
    }

    @Test
    void search_echoesCriteriaBackToForm() throws Exception {
        when(searchUseCase.search(any(ArticleSearchCriteria.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/search")
                        .param("title", "radiology")
                        .param("topic", "PubMed")
                        .param("sourceTier", "ACADEMIC"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("titleParam", "radiology"))
                .andExpect(model().attribute("topicParam", "PubMed"))
                .andExpect(model().attribute("sourceTierParam", "ACADEMIC"));
    }

    @Test
    void search_noResults_rendersEmptyState() throws Exception {
        when(searchUseCase.search(any(ArticleSearchCriteria.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/search").param("title", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searched", true))
                .andExpect(content().string(containsString("No articles matched")));
    }
}
