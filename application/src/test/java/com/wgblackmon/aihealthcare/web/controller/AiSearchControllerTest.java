package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
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
 * <p>Covers tier-based access control (FREE vs MEMBER), usage limit enforcement,
 * multi-model AI synthesis rendering, and usage tracking increments.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-06
 */
@Import(SecurityConfig.class)
@WebMvcTest(AiSearchController.class)
class AiSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConductAiSearchUseCase aiSearchUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private TierGatingService tierGatingService;

    @MockitoBean
    private UsageTrackingPort usageTrackingPort;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private NewsArticle sampleArticle(String id, String title) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, "AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.parse("2026-05-15T10:00:00Z"));
    }

    private void stubMemberTier(String email) {
        Subscriber member = new Subscriber(email, "Member User", true, Instant.now(), SubscriptionTier.MEMBER);
        when(subscriberPort.findByEmail(email)).thenReturn(Optional.of(member));
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
        return new AiSearchResult("search-1", query, List.of(a1), List.of(claude, gpt, perplexity), Instant.now());
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

        verify(aiSearchUseCase, never()).search(anyString(), anyInt());
    }

    // -------------------------------------------------------------------------
    // MEMBER tier — search execution
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "member@example.com")
    void search_memberTier_withQuery_rendersSyntheses() throws Exception {
        stubMemberTier("member@example.com");
        AiSearchResult result = buildSampleResult("AI diagnostics");
        when(aiSearchUseCase.search(eq("AI diagnostics"), eq(20))).thenReturn(result);

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
    @WithMockUser(username = "member@example.com")
    void search_memberTier_withQuery_incrementsUsage() throws Exception {
        stubMemberTier("member@example.com");
        AiSearchResult emptyResult = new AiSearchResult(
                "s1", "test", List.of(), List.of(), Instant.now());
        when(aiSearchUseCase.search(anyString(), anyInt())).thenReturn(emptyResult);

        mockMvc.perform(get("/research/ai-search").param("q", "test query"))
                .andExpect(status().isOk());

        verify(usageTrackingPort).incrementAndGet(eq("member@example.com"), anyString());
    }

    // -------------------------------------------------------------------------
    // MEMBER tier — limit reached
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "member@example.com")
    void search_memberTier_limitReached_showsWarning() throws Exception {
        Subscriber member = new Subscriber("member@example.com", "Member User", true, Instant.now(), SubscriptionTier.MEMBER);
        when(subscriberPort.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        UsageRecord exhausted = new UsageRecord("member@example.com", "2026-06", 200, 200);
        when(usageTrackingPort.getOrCreateUsage(eq("member@example.com"), anyString())).thenReturn(exhausted);
        when(tierGatingService.canQuery(any(UsageRecord.class))).thenReturn(false);

        mockMvc.perform(get("/research/ai-search").param("q", "some query"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("limitReached", true))
                .andExpect(content().string(containsString("Monthly query limit reached")));

        verify(aiSearchUseCase, never()).search(anyString(), anyInt());
    }

    // -------------------------------------------------------------------------
    // MEMBER tier — empty results
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "member@example.com")
    void search_memberTier_emptyResults_showsEmptyState() throws Exception {
        stubMemberTier("member@example.com");
        AiSearchResult emptyResult = new AiSearchResult(
                "s1", "obscure topic", List.of(), List.of(), Instant.now());
        when(aiSearchUseCase.search(anyString(), anyInt())).thenReturn(emptyResult);

        mockMvc.perform(get("/research/ai-search").param("q", "obscure topic"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 0))
                .andExpect(content().string(containsString("No articles matched")));
    }
}
