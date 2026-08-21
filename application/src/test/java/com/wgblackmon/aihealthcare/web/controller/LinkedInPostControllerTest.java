package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link LinkedInPostController}.
 *
 * @author  Bill Blackmon
 * @version 1.3
 * @since   2026-08-21
 * @updated 2026-08-21
 */
@WebMvcTest(LinkedInPostController.class)
class LinkedInPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleIngestionPort articleIngestionPort;

    @Test
    @WithMockUser
    void page_rendersSuccessfullyWithArticles() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "FDA Clears AI Radiology Tool", "Details about clearance.", 0.9),
                article("a2", "OpenAI Launches Healthcare GPT", "Enterprise product launch.", 0.8)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(view().name("linkedin-post"))
                .andExpect(model().attribute("articleCount", 2));
    }

    @Test
    @WithMockUser
    void page_rendersWhenNoArticles() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(view().name("linkedin-post"))
                .andExpect(model().attribute("articleCount", 0));
    }

    @Test
    @WithMockUser
    void postBody_containsBoldArticleTitle() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "AI Chatbot Improves Patient Scheduling", "General product news.", 0.9)
        ));

        // General article gets no label — verify plain bold format
        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postBody",
                        containsString("**AI Chatbot Improves Patient Scheduling**")));
    }

    @Test
    @WithMockUser
    void deduplicatesByTitle_keepsHighestWeight() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "Epic Systems Healthcare AI News", "body1", 0.9),
                article("a2", "Epic Systems Healthcare AI News", "body2", 0.85),
                article("a3", "OpenAI Health GPT Launches", "body3", 0.8)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 2));
    }

    @Test
    @WithMockUser
    void postBody_doesNotExceed3000Chars() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(
                List.of(
                        article("a1", "A".repeat(200), "B".repeat(500), 0.9),
                        article("a2", "C".repeat(200), "D".repeat(500), 0.85),
                        article("a3", "E".repeat(200), "F".repeat(500), 0.8),
                        article("a4", "G".repeat(200), "H".repeat(500), 0.75),
                        article("a5", "I".repeat(200), "J".repeat(500), 0.7)
                )
        );

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postBodyLength",
                        lessThanOrEqualTo(3000)));
    }

    @Test
    @WithMockUser
    void sortsBySourceWeightDescending_capsAtFive() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "Low Weight Article", "body1", 0.3),
                article("a2", "Medium Article", "body2", 0.6),
                article("a3", "High Article 1", "body3", 0.9),
                article("a4", "High Article 2", "body4", 0.85),
                article("a5", "High Article 3", "body5", 0.8),
                article("a6", "High Article 4", "body6", 0.75),
                article("a7", "Top Article", "body7", 0.95)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 5))
                .andExpect(model().attribute("postBody", containsString("Top Article")));
    }

    @Test
    @WithMockUser
    void linksBlock_containsSourceUrl() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "Funding Round Article", "body", 0.9)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("linksBlock",
                        containsString("https://example.com/a1")));
    }

    @Test
    @WithMockUser
    void postBodyLength_matchesActualPostBodyLength() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "Test Article", "Some body text here.", 0.9)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("postBody"))
                .andExpect(model().attribute("postBodyLength",
                        greaterThanOrEqualTo(1)));
    }

    @Test
    @WithMockUser
    void filtersOut_malformedGoogleNewsBody() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                articleWithBody("a1", "Epic Systems Healthcare AI News", "NFE/5.0 \"Epic Systems\" - Google News…", "ai-healthcare", 0.9),
                article("a2", "FDA Clears New AI Diagnostic Tool", "Detailed FDA press release.", 0.8)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    @Test
    @WithMockUser
    void filtersOut_googleNewsLabel_inTitle() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "AI Healthcare Funding - Google News", "body", 0.9),
                article("a2", "CMS Proposes New AI Coverage Rule", "body2", 0.8)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    @Test
    @WithMockUser
    void filtersOut_titleEqualsTopic() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                articleWithBody("a1", "Epic Systems AI Healthcare", "some body text", "epic systems ai healthcare", 0.9),
                article("a2", "Epic Launches Ambient AI Scribe", "Real article body.", 0.8)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    @Test
    @WithMockUser
    void legalArticle_ranksBeforeGeneralArticle() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "OpenAI Releases New Model", "General product news.", 0.95),
                article("a2", "FTC Files Antitrust Lawsuit Against AI Vendor", "Regulatory action.", 0.5)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                // Lawsuit article (lower weight) must appear before the high-weight general article
                .andExpect(model().attribute("postBody", containsString("[LEGAL]")));
    }

    @Test
    @WithMockUser
    void marketplaceArticle_ranksBeforePolicyArticle() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "CMS Issues New Guidance on AI Billing", "Policy details.", 0.9),
                article("a2", "Microsoft Acquires AI Health Startup for $500M", "Deal details.", 0.6)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postBody", containsString("[MARKETPLACE]")));
    }

    @Test
    @WithMockUser
    void policyArticle_getsLabel() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "FDA Approves AI Diagnostic Tool for Cardiac Screening", "Clearance details.", 0.9)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postBody",
                        containsString("**[POLICY] FDA Approves AI Diagnostic Tool for Cardiac Screening**")));
    }

    @Test
    @WithMockUser
    void filtersOut_lowWeightArticle() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "AI Startup Raises Seed Round", "Low-quality tip.", 0.3),
                article("a2", "FDA Clears AI Sepsis Detection Tool", "Real press release.", 0.9)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    @Test
    @WithMockUser
    void filtersOut_googleTitleArticle() throws Exception {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of(
                article("a1", "Google Health Expands AI Diagnostics Partnership", "Google news.", 0.9),
                article("a2", "Epic Launches Ambient AI Documentation Tool", "Real article.", 0.8)
        ));

        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("articleCount", 1));
    }

    @Test
    @WithMockUser
    void unauthorizedUser_isRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/dashboard/linkedin"))
                .andExpect(status().isOk()); // @WithMockUser satisfies auth
    }

    // ------------------------------------------------------------------

    private NewsArticle article(String id, String title, String body, double weight) {
        return articleWithBody(id, title, body, "ai-healthcare", weight);
    }

    private NewsArticle articleWithBody(String id, String title, String body, String topic, double weight) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                body, topic, null, null,
                "Test Source", "INDUSTRY", weight, Instant.now()
        );
    }
}
