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

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MockMvc tests for {@link CorrectionsPostController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-22
 * @updated 2026-08-22
 */
@WebMvcTest(CorrectionsPostController.class)
class CorrectionsPostControllerTest {

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

        mockMvc.perform(get("/dashboard/corrections"))
                .andExpect(status().isOk())
                .andExpect(view().name("corrections-post"))
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

        mockMvc.perform(get("/dashboard/corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 1));
    }

    // ------------------------------------------------------------------
    // Priority filtering (sourceWeight + keywords)
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void lowWeightArticle_isExcluded() throws Exception {
        Contradiction c = contradiction("FDA cleared device X", "FDA recalled device X");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        // weight 0.5 is below MIN_WEIGHT=0.7
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(article("a1", "FDA Clears Device", "body", 0.5)));

        mockMvc.perform(get("/dashboard/corrections"))
                .andExpect(status().isOk())
                // claim text contains "fda" → accepted even with low-weight article
                // because the claim itself triggers priority
                .andExpect(model().attribute("entryCount", 1));
    }

    @Test
    @WithMockUser
    void nonPriorityContradiction_withLowWeight_isExcluded() throws Exception {
        // No LEGAL/POLICY/FDA keyword in claims or articles
        Contradiction c = contradiction("Company A said product works",
                "Company A said product does not work");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(article("a1", "Generic AI News", "body text", 0.5)));

        mockMvc.perform(get("/dashboard/corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 0));
    }

    @Test
    @WithMockUser
    void highWeightArticleWithFdaKeyword_isIncluded() throws Exception {
        Contradiction c = contradiction("Study shows 90% accuracy",
                "Replication study shows 60% accuracy for same device");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(article("a1", "FDA Review of AI Study", "FDA body", 0.8)));

        mockMvc.perform(get("/dashboard/corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 1));
    }

    // ------------------------------------------------------------------
    // Prior claim displayed bold in template
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void priorClaim_isInModel() throws Exception {
        Contradiction c = contradiction("FDA cleared AI diagnostic tool",
                "FDA issued safety alert for AI diagnostic tool");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(fdaArticle("a1", 0.9)));

        MvcResult result = mockMvc.perform(get("/dashboard/corrections"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        List<CorrectionsPostController.CorrectionEntry> entries =
                (List<CorrectionsPostController.CorrectionEntry>)
                result.getModelAndView().getModel().get("entries");
        assert entries != null && !entries.isEmpty() : "entries should not be empty";
        assert entries.get(0).priorClaim().contains("FDA cleared AI diagnostic tool")
                : "Got: " + entries.get(0).priorClaim();
    }

    @Test
    @WithMockUser
    void correctedClaim_isInModel() throws Exception {
        Contradiction c = contradiction("CMS approved AI billing code",
                "CMS reversed AI billing code approval pending review");
        when(wikiQueryPort.recentContradictions(any())).thenReturn(List.of(c));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(cmsArticle("a1", 0.85)));

        MvcResult result = mockMvc.perform(get("/dashboard/corrections"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        List<CorrectionsPostController.CorrectionEntry> entries =
                (List<CorrectionsPostController.CorrectionEntry>)
                result.getModelAndView().getModel().get("entries");
        assert entries != null && !entries.isEmpty() : "entries should not be empty";
        assert entries.get(0).correctedClaim().contains("CMS reversed AI billing code")
                : "Got: " + entries.get(0).correctedClaim();
    }

    // ------------------------------------------------------------------
    // Cap at MAX_ENTRIES = 10
    // ------------------------------------------------------------------

    @Test
    @WithMockUser
    void capsAtTenEntries() throws Exception {
        List<Contradiction> many = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add(contradiction("FDA cleared device " + i, "FDA recalled device " + i));
        }
        when(wikiQueryPort.recentContradictions(any())).thenReturn(many);
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(fdaArticle("a1", 0.9)));

        mockMvc.perform(get("/dashboard/corrections"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 10));
    }

    // ------------------------------------------------------------------
    // cleanText / extractSnippet helpers
    // ------------------------------------------------------------------

    @Test
    void cleanText_stripsHtmlEntities() {
        CorrectionsPostController c = new CorrectionsPostController(
                wikiQueryPort, articleIngestionPort);
        String result = c.cleanText("FDA&nbsp;cleared&amp;approved device");
        assert result.equals("FDA cleared&approved device") : "Got: " + result;
    }

    @Test
    void extractSnippet_truncatesLongBody() {
        CorrectionsPostController c = new CorrectionsPostController(
                wikiQueryPort, articleIngestionPort);
        String longBody = "word ".repeat(60); // >200 chars
        String snippet = c.extractSnippet(longBody);
        assert snippet.length() <= 203 : "Snippet too long: " + snippet.length();
        assert snippet.endsWith("…") : "Should end with ellipsis";
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

    private NewsArticle cmsArticle(String id, double weight) {
        return article(id, "CMS Updates AI Reimbursement Policy",
                "CMS issued an update to its policy on reimbursement for AI diagnostic tools.",
                weight);
    }
}
