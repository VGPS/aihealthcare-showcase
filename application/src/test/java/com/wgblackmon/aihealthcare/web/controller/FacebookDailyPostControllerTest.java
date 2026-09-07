package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.web.util.ArticleToneClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link FacebookDailyPostController}.
 *
 * Validates single merged post format: each article shows title, snippet, and
 * source URL inline. No separate comment block.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-08-22
 * @updated 2026-09-07
 */
@WebMvcTest(FacebookDailyPostController.class)
class FacebookDailyPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleIngestionPort articleIngestionPort;

    @MockitoBean
    private ArticleToneClassifier toneClassifier;

    @BeforeEach
    void setUpToneClassifier() {
        when(toneClassifier.classifyTone(any())).thenReturn("NEUTRAL");
        when(toneClassifier.toneEmoji(anyString())).thenReturn("ℹ️ ");
        when(toneClassifier.toneEmoji(any(NewsArticle.class))).thenReturn("ℹ️ ");
        when(toneClassifier.facebookHashtags(any())).thenReturn(
                "#HealthcareAI #AIinHealthcare #DigitalHealth #HealthTech #BigSkyLabs");
    }

    // ------------------------------------------------------------------
    // Page rendering
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void page_rendersSuccessfully_noArticles() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andExpect(view().name("facebook-daily-post"))
                .andExpect(model().attribute("articleCount", 0));
    }

    @Test
    @WithMockUser
    void page_rendersWithArticles() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(legalArticle("a1", 0.9)));

        mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    // ------------------------------------------------------------------
    // Merged post — contains titles, snippets, and URLs
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void postBody_isPopulated_withArticles() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(legalArticle("a1", 0.9)));

        mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postBodyLength", greaterThan(0)));
    }

    @Test
    @WithMockUser
    void postBody_containsSourceUrl() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(legalArticle("a1", 0.9)));

        MvcResult result = mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andReturn();

        String postBody = (String) result.getModelAndView().getModel().get("postBody");
        assertThat(postBody).contains("example.com");
    }

    @Test
    @WithMockUser
    void postBody_containsSnippet() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(legalArticle("a1", 0.9)));

        MvcResult result = mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andReturn();

        String postBody = (String) result.getModelAndView().getModel().get("postBody");
        assertThat(postBody).contains("FDA has filed a lawsuit");
    }

    @Test
    @WithMockUser
    void postBody_containsHashtags() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(legalArticle("a1", 0.9)));

        MvcResult result = mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andReturn();

        String postBody = (String) result.getModelAndView().getModel().get("postBody");
        assertThat(postBody).contains("#HealthcareAI");
    }

    @Test
    @WithMockUser
    void postBody_isEmpty_whenNoArticles() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postBodyLength", greaterThan(0)));
    }

    // ------------------------------------------------------------------
    // Priority ordering — LEGAL first
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void legalArticle_appearsBeforeGeneral() throws Exception {
        NewsArticle general = article("g1", "AI Tool Improves Patient Scheduling",
                "A new AI tool has shown promise in scheduling.", "INDUSTRY", 0.95);
        NewsArticle legal   = legalArticle("l1", 0.7);

        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(general, legal));

        MvcResult result = mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andReturn();

        String postBody = (String) result.getModelAndView().getModel().get("postBody");
        int legalIdx   = postBody.indexOf("[LEGAL]");
        int generalIdx = postBody.indexOf("AI Tool Improves");
        assertThat(legalIdx).isLessThan(generalIdx);
    }

    // ------------------------------------------------------------------
    // Filtering — low-weight excluded
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void lowWeightArticle_isExcluded() throws Exception {
        NewsArticle low  = article("l1", "Some AI News", "body", "INDUSTRY", 0.3);
        NewsArticle good = legalArticle("a1", 0.9);
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(low, good));

        mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    @Test
    @WithMockUser
    void googleArtifact_isExcluded() throws Exception {
        NewsArticle google = article("g1", "Google News - AI in Healthcare Roundup",
                "body", "INDUSTRY", 0.9);
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(google));

        mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 0));
    }

    // ------------------------------------------------------------------
    // Dedup — duplicate titles collapsed
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void duplicateTitles_areCollapsed() throws Exception {
        NewsArticle a1 = legalArticle("a1", 0.9);
        NewsArticle a2 = legalArticle("a2", 0.8); // same title
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/dashboard/facebook"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    // ------------------------------------------------------------------
    // Classification helper
    // ------------------------------------------------------------------

    @Test
    void classifyLabel_legalArticle_returnsLegalLabel() {
        FacebookDailyPostController ctrl = new FacebookDailyPostController(
                articleIngestionPort, new ArticleToneClassifier());
        NewsArticle a = article("x", "FDA Sues AI Startup Over Lawsuit Settlement",
                "body", "INDUSTRY", 0.9);
        assertThat(ctrl.classifyLabel(a)).isEqualTo("[LEGAL] ");
    }

    @Test
    void classifyLabel_marketplaceArticle_returnsMarketplaceLabel() {
        FacebookDailyPostController ctrl = new FacebookDailyPostController(
                articleIngestionPort, new ArticleToneClassifier());
        NewsArticle a = article("x", "Epic Systems acquires AI startup for $2.1B",
                "body", "INDUSTRY", 0.9);
        assertThat(ctrl.classifyLabel(a)).isEqualTo("[MARKETPLACE] ");
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    @Test
    void cleanText_stripsHtmlAndEntities() {
        FacebookDailyPostController ctrl = new FacebookDailyPostController(
                articleIngestionPort, new ArticleToneClassifier());
        assertThat(ctrl.cleanText("FDA&nbsp;cleared <b>device</b>")).isEqualTo("FDA cleared device");
    }

    @Test
    void extractSnippet_truncatesAtWordBoundary() {
        FacebookDailyPostController ctrl = new FacebookDailyPostController(
                articleIngestionPort, new ArticleToneClassifier());
        String long200 = "word ".repeat(50);
        String result  = ctrl.extractSnippet(long200, 100);
        assertThat(result.length()).isLessThanOrEqualTo(101);
        assertThat(result).endsWith("…");
    }

    @Test
    void truncate_addsEllipsis() {
        FacebookDailyPostController ctrl = new FacebookDailyPostController(
                articleIngestionPort, new ArticleToneClassifier());
        String result = ctrl.truncate("The FDA has issued new guidance on AI devices today", 30);
        assertThat(result.length()).isLessThanOrEqualTo(31);
        assertThat(result).endsWith("…");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private NewsArticle article(String id, String title, String body, String tier, double weight) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                body, "ai-healthcare", null, null,
                "Test Source", tier, weight, Instant.now()
        );
    }

    private NewsArticle legalArticle(String id, double weight) {
        return article(id,
                "FDA Files Lawsuit Against AI Diagnostic Company",
                "The FDA has filed a lawsuit against an AI diagnostic company for marketing an unapproved device.",
                "REGULATORY", weight);
    }
}
