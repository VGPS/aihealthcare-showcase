package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link FacebookCorrectionsController}.
 *
 * Verifies page rendering, priority filtering, character-limit enforcement,
 * and comment block content for the Facebook corrections post generator.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-22
 * @updated 2026-08-22
 */
@WebMvcTest(FacebookCorrectionsController.class)
class FacebookCorrectionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WikiQueryPort wikiQueryPort;

    @MockitoBean
    private ArticleIngestionPort articleIngestionPort;

    // ------------------------------------------------------------------
    // Page rendering
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void page_rendersSuccessfully() throws Exception {
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andExpect(view().name("facebook-corrections"))
                .andExpect(model().attribute("entryCount", 0));
    }

    @Test
    @WithMockUser
    void page_rendersWithEntries() throws Exception {
        Contradiction c = contradiction("FDA approved AI device",
                "FDA revoked approval citing safety concerns");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(fdaArticle("a1", 0.9)));

        mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 1));
    }

    // ------------------------------------------------------------------
    // Priority filtering
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void fdaClaimKeyword_isIncluded_regardlessOfArticleWeight() throws Exception {
        Contradiction c = contradiction("FDA cleared AI diagnostic tool",
                "FDA issued safety alert for AI diagnostic tool");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(article("a1", "Some Title", "body", 0.5)));

        mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 1));
    }

    @Test
    @WithMockUser
    void nonPriorityLowWeight_isExcluded() throws Exception {
        Contradiction c = contradiction("Company A said product works",
                "Company A said product does not work");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(article("a1", "Generic AI News", "body", 0.5)));

        mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 0));
    }

    @Test
    @WithMockUser
    void cmsKeyword_inArticle_isIncluded() throws Exception {
        Contradiction c = contradiction("Study shows 90% accuracy",
                "Replication study shows 60% accuracy for same device");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(article("a1", "CMS Updates AI Reimbursement", "CMS rule", 0.85)));

        mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 1));
    }

    // ------------------------------------------------------------------
    // Post body character limit (390)
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void postBody_isPopulated() throws Exception {
        Contradiction c = contradiction("FDA cleared AI diagnostic tool",
                "FDA issued safety alert for AI diagnostic tool");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(fdaArticle("a1", 0.9)));

        mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("postBody"))
                .andExpect(model().attribute("postBodyLength", greaterThan(0)));
    }

    @Test
    @WithMockUser
    void postBody_doesNotExceed390Chars() throws Exception {
        List<Contradiction> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(contradiction(
                    "FDA cleared AI diagnostic device number " + i + " for patient use in clinical settings with very long description",
                    "FDA issued safety alert for AI diagnostic device number " + i + " citing multiple adverse events in clinical trials"));
        }
        when(wikiQueryPort.recentContradictions(any())).thenReturn(many);
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(fdaArticle("a1", 0.9)));

        mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postBodyLength", lessThanOrEqualTo(390)));
    }

    // ------------------------------------------------------------------
    // Comment block
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void commentBlock_containsWikiUrl() throws Exception {
        Contradiction c = contradiction("FDA cleared AI diagnostic tool",
                "FDA issued safety alert for AI diagnostic tool");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(fdaArticle("a1", 0.9)));

        MvcResult result = mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andReturn();
        String commentBlock = (String) result.getModelAndView().getModel().get("commentBlock");
        assert commentBlock != null && commentBlock.contains("/wiki/")
                : "Comment block should contain wiki URL";
    }

    @Test
    @WithMockUser
    void commentBlock_containsDisclaimer() throws Exception {
        Contradiction c = contradiction("FDA cleared AI diagnostic tool",
                "FDA revoked AI device approval");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(fdaArticle("a1", 0.9)));

        MvcResult result = mockMvc.perform(get("/dashboard/facebook-corrections"))
                .andExpect(status().isOk())
                .andReturn();
        String commentBlock = (String) result.getModelAndView().getModel().get("commentBlock");
        assert commentBlock != null && commentBlock.contains("NOT been individually verified")
                : "Comment block should contain disclaimer";
    }

    // ------------------------------------------------------------------
    // Helpers — cleanText / truncate
    // ------------------------------------------------------------------

    @Test
    void cleanText_stripsHtmlEntities() {
        FacebookCorrectionsController c = new FacebookCorrectionsController(
                wikiQueryPort, articleIngestionPort);
        String result = c.cleanText("FDA&nbsp;cleared&amp;approved device");
        assert result.equals("FDA cleared&approved device") : "Got: " + result;
    }

    @Test
    void truncate_addsEllipsis_forLongText() {
        FacebookCorrectionsController c = new FacebookCorrectionsController(
                wikiQueryPort, articleIngestionPort);
        String long140 = "word ".repeat(40); // > 140 chars
        String result  = c.truncate(long140, 140);
        assert result.length() <= 141 : "Truncated too long: " + result.length();
        assert result.endsWith("…") : "Should end with ellipsis";
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Contradiction contradiction(String priorClaim, String newClaim) {
        SourceRef priorRef = new SourceRef("prior-1", "Test Source", LocalDate.now(), null);
        SourceRef newRef   = new SourceRef("new-1",   "Test Source", LocalDate.now(), null);
        return new Contradiction(
                "test-page-slug",
                priorClaim,
                newClaim,
                List.of(priorRef),
                List.of(newRef),
                Instant.now()
        );
    }

    private NewsArticle article(String id, String title, String body, double weight) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                body, "ai-healthcare", null, null,
                "Test Source", "INDUSTRY", weight, Instant.now()
        );
    }

    private NewsArticle fdaArticle(String id, double weight) {
        return article(id, "FDA Issues Guidance on AI Medical Devices",
                "The FDA has issued new guidance on artificial intelligence medical devices.",
                weight);
    }
}
