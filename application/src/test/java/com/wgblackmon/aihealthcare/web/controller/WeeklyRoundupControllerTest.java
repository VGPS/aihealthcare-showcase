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

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link WeeklyRoundupController}.
 *
 * Validates LEGAL + COMPETITOR tier filtering, topic summaries, LinkedIn post
 * formatting, Substack article generation, and edge cases.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-19
 * @updated 2026-09-19
 */
@WebMvcTest(WeeklyRoundupController.class)
class WeeklyRoundupControllerTest {

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
        when(toneClassifier.linkedInHashtags(any())).thenReturn(
                "#HealthcareAI #AIinHealthcare #DigitalHealth #HealthTech #MedicalInnovation");
    }

    @Test
    @WithMockUser
    void weeklyRoundup_withMixedArticles_filtersToTargetTiers() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(mixedTierArticles());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(view().name("weekly-roundup"))
                .andExpect(model().attribute("articleCount", 3))
                .andExpect(model().attribute("legalCount", 1))
                .andExpect(model().attribute("competitorCount", 2));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_noArticles_showsEmptyState() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(view().name("weekly-roundup"))
                .andExpect(model().attribute("articleCount", 0))
                .andExpect(model().attribute("legalCount", 0))
                .andExpect(model().attribute("competitorCount", 0));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_linkedinBody_containsWeeklyHeader() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(singleLegalArticle());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("linkedinBody",
                        containsString("Weekly Intel Roundup")));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_linkedinBody_containsTopicSummaries() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(singleLegalArticle());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("linkedinBody",
                        containsString("LEGAL & REGULATORY")))
                .andExpect(model().attribute("linkedinBody",
                        containsString("COMPETITOR WATCH")));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_substackArticle_containsMarkdownHeaders() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(singleLegalArticle());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("substackArticle",
                        containsString("# AI in Healthcare")))
                .andExpect(model().attribute("substackArticle",
                        containsString("## Legal & Regulatory")))
                .andExpect(model().attribute("substackArticle",
                        containsString("## Sources")));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_substackArticle_containsSourceLinks() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(singleLegalArticle());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("substackArticle",
                        containsString("[FDA Clears New AI Diagnostic Tool]")));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_legalArticlesSortedFirst() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(mixedTierArticles());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("linkedinBody",
                        containsString("1. ℹ️ **[LEGAL]")));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_caps10Articles() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(fifteenArticles());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 10));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_deduplicatesByTitle() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(duplicateTitleArticles());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    @Test
    @WithMockUser
    void weeklyRoundup_linkedinBodyLength_withinLimit() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(fifteenArticles());

        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("linkedinBodyLength",
                        org.hamcrest.Matchers.lessThanOrEqualTo(2900)));
    }

    @Test
    void weeklyRoundup_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/dashboard/weekly-roundup"))
                .andExpect(status().isUnauthorized());
    }

    // ── Test data factories ──

    private List<NewsArticle> mixedTierArticles() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("legal-1", "FDA Clears New AI Diagnostic Tool",
                "LEGAL", "AI Healthcare Legal", 0.9));
        articles.add(article("comp-1", "Epic Systems Launches AI Scribe Update",
                "COMPETITOR", "Epic Systems Healthcare AI", 0.85));
        articles.add(article("comp-2", "Anthropic Partners with Hospital Chain",
                "COMPETITOR", "Anthropic Healthcare", 0.7));
        articles.add(article("academic-1", "Study Shows AI Improves Outcomes",
                "ACADEMIC", "PubMed AI Healthcare", 0.8));
        articles.add(article("industry-1", "HealthTech Quarterly Revenue Report",
                "INDUSTRY", "General AI Healthcare News", 0.6));
        return articles;
    }

    private List<NewsArticle> singleLegalArticle() {
        return List.of(article("legal-1", "FDA Clears New AI Diagnostic Tool",
                "LEGAL", "AI Healthcare Legal", 0.9));
    }

    private List<NewsArticle> fifteenArticles() {
        List<NewsArticle> articles = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            articles.add(article("legal-" + i, "Legal Article " + i,
                    "LEGAL", "AI Healthcare Legal", 0.9 - (i * 0.02)));
        }
        for (int i = 1; i <= 7; i++) {
            articles.add(article("comp-" + i, "Competitor Article " + i,
                    "COMPETITOR", "Epic Systems Healthcare AI", 0.85 - (i * 0.02)));
        }
        return articles;
    }

    private List<NewsArticle> duplicateTitleArticles() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("legal-1", "FDA Clears AI Tool",
                "LEGAL", "AI Healthcare Legal", 0.9));
        articles.add(article("legal-2", "FDA Clears AI Tool",
                "LEGAL", "AI Healthcare Legal", 0.85));
        return articles;
    }

    private NewsArticle article(String id, String title, String tier,
                                String topic, double weight) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "Body text for " + title,
                topic, "Test Author", null,
                "Test Source", tier, weight, Instant.now()
        );
    }
}
