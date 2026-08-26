package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.ModelInfo;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link AiSearchController}.
 *
 * <p>Covers tier-based access control (FREE vs SUBSCRIBER), usage limit enforcement,
 * multi-model AI synthesis rendering, and usage tracking increments.
 *
 * @author  Bill Blackmon
 * @version 2.2
 * @since   2026-06-02
 * @updated 2026-08-25
 */
@Import(SecurityConfig.class)
@WebMvcTest(AiSearchController.class)
class AiSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private ConductAiSearchUseCase aiSearchUseCase;

    @MockitoBean
    private ArticleSearchPort articleSearchPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private TierGatingService tierGatingService;

    @MockitoBean
    private UsageTrackingPort usageTrackingPort;

    private static final List<ModelInfo> DEFAULT_MODELS = List.of(
            new ModelInfo("Claude", "claude-sonnet-4-6"),
            new ModelInfo("GPT", "gpt-4o"),
            new ModelInfo("Perplexity", "sonar"));

    @BeforeEach
    void setUp() {
        when(aiSearchUseCase.availableModels()).thenReturn(DEFAULT_MODELS);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private NewsArticle sampleArticle(String id, String title) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, "AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.parse("2026-05-15T10:00:00Z"));
    }

    private void stubSubscriberTier(String email) {
        Subscriber subscriber = new Subscriber(email, "Subscriber User", true, Instant.now(), SubscriptionTier.SUBSCRIBER, null, null, null);
        when(subscriberPort.findByEmail(email)).thenReturn(Optional.of(subscriber));
        UsageRecord usage = new UsageRecord(email, "2026-06", 5, 200);
        when(usageTrackingPort.getOrCreateUsage(eq(email), anyString())).thenReturn(usage);
        when(tierGatingService.canQuery(any(UsageRecord.class))).thenReturn(true);
    }

    private void stubFreeTier() {
        when(subscriberPort.findByEmail(any())).thenReturn(Optional.empty());
    }

    private AiSearchResult buildSampleResult(String query) {
        NewsArticle a1 = sampleArticle("id-1", "AI Diagnostics in Radiology");
        AiSearchSynthesis claude = new AiSearchSynthesis(
                "Claude", "Claude found key insights about AI diagnostics.",
                List.of("AI improves diagnostic accuracy", "Radiology adoption accelerating"),
                Instant.now());
        AiSearchSynthesis gpt = new AiSearchSynthesis(
                "GPT", "GPT analysis shows growing adoption of AI tools.",
                List.of("ML models outperform baseline", "Cost reduction documented"),
                Instant.now());
        AiSearchSynthesis perplexity = new AiSearchSynthesis(
                "Perplexity", "Perplexity synthesis highlights real-time research trends.",
                List.of("Live source citations available", "Sonar model excels at aggregation"),
                Instant.now());
        return new AiSearchResult("search-1", query, List.of(a1), List.of(claude, gpt, perplexity), List.of(), Instant.now());
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    @Test
    void search_noAuth_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/research/ai-search"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // -------------------------------------------------------------------------
    // FREE tier — access denied
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "free@example.com")
    void search_freeTier_showsUpgradeBanner() throws Exception {
        stubFreeTier();

        mockMvc.perform(get("/research/ai-search"))
                .andExpect(status().isOk())
                .andExpect(view().name("ai-search"))
                .andExpect(model().attribute("accessDenied", true))
                .andExpect(content().string(containsString("Upgrade")));

        verify(aiSearchUseCase, never()).search(anyString(), anyInt(), any());
    }

    // -------------------------------------------------------------------------
    // SUBSCRIBER tier — search execution
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "subscriber@example.com")
    void search_subscriberTier_withQuery_rendersSyntheses() throws Exception {
        stubSubscriberTier("subscriber@example.com");
        AiSearchResult result = buildSampleResult("AI diagnostics");
        when(aiSearchUseCase.search(eq("AI diagnostics"), eq(20), any())).thenReturn(result);

        mockMvc.perform(get("/research/ai-search").param("q", "AI diagnostics"))
                .andExpect(status().isOk())
                .andExpect(view().name("ai-search"))
                .andExpect(model().attributeExists("syntheses", "articles", "articleDates"))
                .andExpect(model().attribute("articleCount", 1))
                .andExpect(content().string(containsString("Claude")))
                .andExpect(content().string(containsString("GPT")))
                .andExpect(content().string(containsString("Perplexity")))
                .andExpect(content().string(containsString("AI Diagnostics in Radiology")));
    }

    @Test
    @WithMockUser(username = "subscriber@example.com")
    void search_subscriberTier_withQuery_incrementsUsage() throws Exception {
        stubSubscriberTier("subscriber@example.com");
        AiSearchResult emptyResult = new AiSearchResult(
                "s1", "test", List.of(), List.of(), List.of(), Instant.now());
        when(aiSearchUseCase.search(anyString(), anyInt(), any())).thenReturn(emptyResult);

        mockMvc.perform(get("/research/ai-search").param("q", "test query"))
                .andExpect(status().isOk());

        verify(usageTrackingPort).incrementAndGet(eq("subscriber@example.com"), anyString());
    }

    @Test
    @WithMockUser(username = "subscriber@example.com")
    void search_subscriberTier_partialNoMatch_showsNoteAboutDecliningModels() throws Exception {
        stubSubscriberTier("subscriber@example.com");
        NewsArticle a1 = sampleArticle("id-1", "Unrelated hospital merger news");
        AiSearchSynthesis claude = new AiSearchSynthesis(
                "Claude", "Claude found key insights.", List.of("Finding"), Instant.now());
        AiSearchResult result = new AiSearchResult(
                "search-1", "Grelin Health business model", List.of(a1), List.of(claude),
                List.of("GPT", "Perplexity"), Instant.now());
        when(aiSearchUseCase.search(eq("Grelin Health business model"), eq(20), any())).thenReturn(result);

        mockMvc.perform(get("/research/ai-search").param("q", "Grelin Health business model"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("noMatchModels", List.of("GPT", "Perplexity")))
                .andExpect(content().string(containsString("GPT, Perplexity")))
                .andExpect(content().string(containsString("found no relevant match")));
    }

    // -------------------------------------------------------------------------
    // SUBSCRIBER tier — limit reached
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "subscriber@example.com")
    void search_subscriberTier_limitReached_showsWarning() throws Exception {
        Subscriber subscriber = new Subscriber("subscriber@example.com", "Subscriber User", true, Instant.now(), SubscriptionTier.SUBSCRIBER, null, null, null);
        when(subscriberPort.findByEmail("subscriber@example.com")).thenReturn(Optional.of(subscriber));
        UsageRecord exhausted = new UsageRecord("subscriber@example.com", "2026-06", 200, 200);
        when(usageTrackingPort.getOrCreateUsage(eq("subscriber@example.com"), anyString())).thenReturn(exhausted);
        when(tierGatingService.canQuery(any(UsageRecord.class))).thenReturn(false);

        mockMvc.perform(get("/research/ai-search").param("q", "some query"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("limitReached", true))
                .andExpect(content().string(containsString("Monthly query limit reached")));

        verify(aiSearchUseCase, never()).search(anyString(), anyInt(), any());
    }

    // -------------------------------------------------------------------------
    // SUBSCRIBER tier — empty results
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "subscriber@example.com")
    void search_subscriberTier_emptyResults_showsEmptyState() throws Exception {
        stubSubscriberTier("subscriber@example.com");
        AiSearchResult emptyResult = new AiSearchResult(
                "s1", "obscure topic", List.of(), List.of(), List.of(), Instant.now());
        when(aiSearchUseCase.search(anyString(), anyInt(), any())).thenReturn(emptyResult);

        mockMvc.perform(get("/research/ai-search").param("q", "obscure topic"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 0))
                .andExpect(content().string(containsString("No articles found matching")));
    }

    // -------------------------------------------------------------------------
    // AI synthesis failure — graceful fallback to vector-only
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "subscriber@example.com")
    void search_subscriberTier_synthesisFailure_fallsBackToVectorOnly() throws Exception {
        stubSubscriberTier("subscriber@example.com");
        NewsArticle a1 = sampleArticle("id-1", "Fallback Article");
        when(aiSearchUseCase.search(anyString(), anyInt(), any()))
                .thenThrow(new RuntimeException("API timeout"));
        when(articleSearchPort.findSimilar(eq("fallback query"), eq(20)))
                .thenReturn(List.of(a1));

        mockMvc.perform(get("/research/ai-search").param("q", "fallback query"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1))
                .andExpect(content().string(containsString("AI models found no relevant match")));
    }

    // -------------------------------------------------------------------------
    // Available models — dynamic checkbox rendering
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "subscriber@example.com")
    void search_subscriberTier_passesAvailableModelsToTemplate() throws Exception {
        stubSubscriberTier("subscriber@example.com");

        mockMvc.perform(get("/research/ai-search"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("availableModels"))
                .andExpect(model().attribute("availableModels",
                        List.of("Claude", "GPT", "Perplexity")));
    }
}
