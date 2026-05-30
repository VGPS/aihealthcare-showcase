package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
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
 * MockMvc slice tests for {@link SemanticSearchController}.
 *
 * <p>Covers tier-based access control (FREE vs MEMBER), usage limit enforcement,
 * query execution via {@link ArticleSearchPort#findSimilar(String, int)}, and
 * usage tracking increments.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-30
 * @updated 2026-05-30
 */
@Import(SecurityConfig.class)
@WebMvcTest(SemanticSearchController.class)
class SemanticSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleSearchPort articleSearchPort;

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
                "Body text for " + title, "General AI Healthcare News", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.parse("2026-05-15T10:00:00Z"));
    }

    private void stubMemberTier(String email) {
        Subscriber member = new Subscriber(email, "Member User", true, Instant.now(), SubscriptionTier.MEMBER);
        when(subscriberPort.findByEmail(email)).thenReturn(Optional.of(member));
        UsageRecord usage = new UsageRecord(email, "2026-05", 5, 200);
        when(usageTrackingPort.getOrCreateUsage(eq(email), anyString())).thenReturn(usage);
        when(tierGatingService.canQuery(any(UsageRecord.class))).thenReturn(true);
    }

    private void stubFreeTier() {
        when(subscriberPort.findByEmail(any())).thenReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    @Test
    void search_noAuth_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/research/search"))
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

        mockMvc.perform(get("/research/search"))
                .andExpect(status().isOk())
                .andExpect(view().name("semantic-search"))
                .andExpect(model().attribute("accessDenied", true))
                .andExpect(content().string(containsString("Upgrade")));

        verify(articleSearchPort, never()).findSimilar(anyString(), anyInt());
    }

    // -------------------------------------------------------------------------
    // MEMBER tier — search form
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "member@example.com")
    void search_memberTier_showsSearchForm() throws Exception {
        stubMemberTier("member@example.com");

        mockMvc.perform(get("/research/search"))
                .andExpect(status().isOk())
                .andExpect(view().name("semantic-search"))
                .andExpect(model().attributeDoesNotExist("accessDenied"))
                .andExpect(model().attributeDoesNotExist("limitReached"))
                .andExpect(content().string(containsString("Search Articles")));
    }

    // -------------------------------------------------------------------------
    // MEMBER tier — search execution
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "member@example.com")
    void search_memberTier_withQuery_returnsResults() throws Exception {
        stubMemberTier("member@example.com");
        NewsArticle a1 = sampleArticle("id-1", "AI Diagnostics in Radiology");
        NewsArticle a2 = sampleArticle("id-2", "Machine Learning in Pathology");
        when(articleSearchPort.findSimilar(eq("AI diagnostics"), eq(10)))
                .thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/research/search").param("q", "AI diagnostics"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("results", "resultDates", "resultSnippets"))
                .andExpect(model().attribute("resultCount", 2))
                .andExpect(content().string(containsString("AI Diagnostics in Radiology")))
                .andExpect(content().string(containsString("Machine Learning in Pathology")));
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void search_memberTier_withQuery_incrementsUsage() throws Exception {
        stubMemberTier("member@example.com");
        when(articleSearchPort.findSimilar(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/research/search").param("q", "test query"))
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
        UsageRecord exhausted = new UsageRecord("member@example.com", "2026-05", 200, 200);
        when(usageTrackingPort.getOrCreateUsage(eq("member@example.com"), anyString())).thenReturn(exhausted);
        when(tierGatingService.canQuery(any(UsageRecord.class))).thenReturn(false);

        mockMvc.perform(get("/research/search").param("q", "some query"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("limitReached", true))
                .andExpect(content().string(containsString("Monthly query limit reached")));

        verify(articleSearchPort, never()).findSimilar(anyString(), anyInt());
    }

    // -------------------------------------------------------------------------
    // MEMBER tier — empty results
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "member@example.com")
    void search_memberTier_emptyResults_showsEmptyState() throws Exception {
        stubMemberTier("member@example.com");
        when(articleSearchPort.findSimilar(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/research/search").param("q", "obscure topic"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("resultCount", 0))
                .andExpect(content().string(containsString("No articles matched")));
    }

    // -------------------------------------------------------------------------
    // MEMBER tier — custom topK
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "member@example.com")
    void search_memberTier_customTopK_respectsParam() throws Exception {
        stubMemberTier("member@example.com");
        when(articleSearchPort.findSimilar(eq("AI in surgery"), eq(25))).thenReturn(List.of());

        mockMvc.perform(get("/research/search")
                        .param("q", "AI in surgery")
                        .param("topK", "25"))
                .andExpect(status().isOk());

        verify(articleSearchPort).findSimilar(eq("AI in surgery"), eq(25));
    }
}
