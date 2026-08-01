package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
 * <p>Covers {@code GET /dashboard} (Daily Briefing),
 * {@code GET /dashboard/articles} (per-topic article detail with sort),
 * {@code GET /dashboard/news} (all topics grouped with section headers),
 * and {@code GET /dashboard/search} (multi-field article search).
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-05-04
 * @updated 2026-08-01
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ApiKeyPort apiKeyPort;
    @MockitoBean private ArticleIngestionPort articleIngestionPort;
    @MockitoBean private NewsTopicProperties newsTopicProperties;
    @MockitoBean private TopicSummaryPort topicSummaryPort;
    @MockitoBean private SubscriberPort subscriberPort;
    @MockitoBean private TierGatingService tierGatingService;
    @MockitoBean private SearchArticlesUseCase searchUseCase;
    @MockitoBean private WatchlistPort watchlistPort;
    @MockitoBean private WatchlistMatchPort watchlistMatchPort;
    @MockitoBean private DetectTrendsUseCase detectTrendsUseCase;
    @MockitoBean private MonitorRegulatoryEventsUseCase regulatoryUseCase;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private NewsArticle sampleArticle(String title, Instant publishedAt) {
        return new NewsArticle(
                "id-1", title, URI.create("https://example.com/article"),
                "body text", "PubMed AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, publishedAt);
    }

    @BeforeEach
    void stubDefaults() {
        // Dashboard defaults
        when(watchlistPort.findByUser(anyString())).thenReturn(List.of());
        when(watchlistMatchPort.findByUser(anyString(), anyInt())).thenReturn(List.of());
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of());
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.empty());
        when(regulatoryUseCase.getRecentEvents(anyInt())).thenReturn(List.of());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(0)))
                .thenReturn(List.of());
        when(subscriberPort.findByEmail(any())).thenReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // GET /dashboard — Daily Briefing
    // -------------------------------------------------------------------------

    @Test
    void dashboard_returns200AndBriefingView() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    void dashboard_modelContainsBriefingAttributes() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("hasWatchlist", "watchlistMatches",
                        "headlines", "risingTrends", "regulatoryEvents", "legalPulse", "tier"));
    }

    @Test
    void dashboard_showsHeadlinesWhenArticlesExist() throws Exception {
        NewsArticle article = sampleArticle("AI Breakthrough in Diagnostics", Instant.now());
        when(articleIngestionPort.fetchRecentArticles(2)).thenReturn(List.of(article));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI Breakthrough in Diagnostics")));
    }

    @Test
    void dashboard_showsWatchlistAlertsWhenMatchesExist() throws Exception {
        WatchlistItem item = new WatchlistItem("item-1", "user", WatchlistItemType.KEYWORD,
                "FDA clearance", "FDA clearance", Instant.now());
        WatchlistMatch match = new WatchlistMatch("match-1", "item-1", "article-1",
                Instant.now(), "FDA granted clearance for AI device...");
        when(watchlistPort.findByUser("user")).thenReturn(List.of(item));
        when(watchlistMatchPort.findByUser("user", 5)).thenReturn(List.of(match));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasWatchlist", true))
                .andExpect(content().string(containsString("Watchlist Alerts")))
                .andExpect(content().string(containsString("FDA granted clearance")));
    }

    @Test
    void dashboard_hidesWatchlistWhenNoMatches() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasWatchlist", false));
    }

    @Test
    void dashboard_showsTrendingKeywords() throws Exception {
        TrendSignal signal = new TrendSignal("GPT-5 healthcare", 15, 5, 3, 3.0,
                TrendDirection.RISING, Instant.now(), List.of(), "GPT-5 is transforming diagnostics");
        TrendSnapshot snapshot = new TrendSnapshot(Instant.now(), 30,
                List.of(signal), List.of(), List.of(), 50);
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("GPT-5 healthcare")));
    }

    @Test
    void dashboard_showsRegulatoryEvents() throws Exception {
        RegulatoryEvent event = new RegulatoryEvent("evt-1", RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "AI Radiology Device Cleared", "Summary",
                "K241234", "Acme AI", "RadiologyBot", "https://fda.gov/k241234",
                null, Instant.now(), Instant.now(), List.of("radiology"),
                null, null, null, null);
        when(regulatoryUseCase.getRecentEvents(3)).thenReturn(List.of(event));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI Radiology Device Cleared")))
                .andExpect(content().string(containsString("Acme AI")));
    }

    @Test
    void dashboard_showsUpgradePromptForFreeUsers() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Get the Full Picture")));
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
                .andExpect(content().string(containsString("Upgrade to Subscriber")));
    }

    @Test
    void news_subscriberTier_noArchiveBanner() throws Exception {
        Subscriber subscriber = new Subscriber("user", "Subscriber User", true, Instant.now(), SubscriptionTier.SUBSCRIBER, null, null, null);
        when(subscriberPort.findByEmail("user")).thenReturn(Optional.of(subscriber));
        when(tierGatingService.archiveDaysFor(SubscriptionTier.SUBSCRIBER)).thenReturn(0);
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(0)))
                .thenReturn(List.of());

        String html = mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("archiveLimited", false))
                .andReturn().getResponse().getContentAsString();

        assert !html.contains("Upgrade to Subscriber") : "Subscriber should not see upgrade banner";
    }

    @Test
    void news_subscriberTier_usesUnlimitedArchive() throws Exception {
        Subscriber subscriber = new Subscriber("user", "Subscriber User", true, Instant.now(), SubscriptionTier.SUBSCRIBER, null, null, null);
        when(subscriberPort.findByEmail("user")).thenReturn(Optional.of(subscriber));
        when(tierGatingService.archiveDaysFor(SubscriptionTier.SUBSCRIBER)).thenReturn(0);
        NewsArticle article = sampleArticle("Old Article", Instant.parse("2025-01-01T10:00:00Z"));
        when(newsTopicProperties.getTopics()).thenReturn(List.of("General AI Healthcare News"));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("General AI Healthcare News"), eq(0)))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/dashboard/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Old Article")));
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/articles — tier gating for New AI Healthcare Companies
    // -------------------------------------------------------------------------

    @Test
    void articles_companiesTopic_freeUser_showsAccessDenied() throws Exception {
        when(subscriberPort.findByEmail("user")).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/articles")
                        .param("topic", "New AI Healthcare Companies"))
                .andExpect(status().isOk())
                .andExpect(view().name("articles"))
                .andExpect(model().attribute("accessDenied", true))
                .andExpect(content().string(containsString("Subscriber feature")));
    }

    @Test
    void articles_companiesTopic_subscriberUser_showsArticles() throws Exception {
        Subscriber subscriber = new Subscriber("user", "Subscriber User", true, Instant.now(), SubscriptionTier.SUBSCRIBER, null, null, null);
        when(subscriberPort.findByEmail("user")).thenReturn(Optional.of(subscriber));
        when(articleIngestionPort.fetchAllByTopic(eq("New AI Healthcare Companies")))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/articles")
                        .param("topic", "New AI Healthcare Companies"))
                .andExpect(status().isOk())
                .andExpect(view().name("articles"))
                .andExpect(model().attributeDoesNotExist("accessDenied"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void articles_companiesTopic_adminUser_bypassesGating() throws Exception {
        when(articleIngestionPort.fetchAllByTopic(eq("New AI Healthcare Companies")))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/articles")
                        .param("topic", "New AI Healthcare Companies"))
                .andExpect(status().isOk())
                .andExpect(view().name("articles"))
                .andExpect(model().attributeDoesNotExist("accessDenied"));
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
                        .param("sourceName", "PubMed"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("titleParam", "radiology"))
                .andExpect(model().attribute("topicParam", "PubMed"))
                .andExpect(model().attribute("sourceNameParam", "PubMed"));
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
