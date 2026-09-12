package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link ArticleController}.
 *
 * <p>Covers {@code GET /api/v1/articles} with archive depth gating
 * based on the {@code X-Subscriber-Email} header.  FREE tier subscribers
 * receive date-limited results; SUBSCRIBER tier receives unlimited results.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-30
 * @updated 2026-05-30
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(ArticleController.class)
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private ArticleIngestionPort ingestionPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private TierGatingService tierGatingService;

    @MockitoBean
    private TierResolver tierResolver;

    private NewsArticle sampleArticle(String id, String title) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "body", "AI Healthcare", "Author",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.now());
    }

    @Test
    void listArticles_noHeader_defaultsToFreeTierGating() throws Exception {
        when(tierResolver.resolveTier((String) null)).thenReturn(SubscriptionTier.FREE);
        when(tierGatingService.archiveDaysFor(SubscriptionTier.FREE)).thenReturn(7);
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare"), eq(7)))
                .thenReturn(List.of(sampleArticle("a1", "Article 1")));

        mockMvc.perform(get("/api/v1/articles").param("topic", "AI Healthcare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Article 1"));

        verify(ingestionPort).fetchByTopicWithArchiveLimit("AI Healthcare", 7);
    }

    @Test
    void listArticles_freeSubscriber_usesArchiveLimit() throws Exception {
        when(tierResolver.resolveTier("free@test.com")).thenReturn(SubscriptionTier.FREE);
        when(tierGatingService.archiveDaysFor(SubscriptionTier.FREE)).thenReturn(7);
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare"), eq(7)))
                .thenReturn(List.of(sampleArticle("a1", "Recent Article")));

        mockMvc.perform(get("/api/v1/articles")
                        .param("topic", "AI Healthcare")
                        .header("X-Subscriber-Email", "free@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(ingestionPort).fetchByTopicWithArchiveLimit("AI Healthcare", 7);
    }

    @Test
    void listArticles_subscriberTier_usesUnlimitedArchive() throws Exception {
        when(tierResolver.resolveTier("subscriber@test.com")).thenReturn(SubscriptionTier.SUBSCRIBER);
        when(tierGatingService.archiveDaysFor(SubscriptionTier.SUBSCRIBER)).thenReturn(0);
        when(ingestionPort.fetchArticles(eq("AI Healthcare"), eq(20)))
                .thenReturn(List.of(sampleArticle("a1", "All Articles")));

        mockMvc.perform(get("/api/v1/articles")
                        .param("topic", "AI Healthcare")
                        .header("X-Subscriber-Email", "subscriber@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(ingestionPort).fetchArticles("AI Healthcare", 20);
    }

    @Test
    void listArticles_freeTier_respectsLimitParam() throws Exception {
        when(tierResolver.resolveTier((String) null)).thenReturn(SubscriptionTier.FREE);
        when(tierGatingService.archiveDaysFor(SubscriptionTier.FREE)).thenReturn(7);
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare"), eq(7)))
                .thenReturn(List.of(
                        sampleArticle("a1", "Article 1"),
                        sampleArticle("a2", "Article 2"),
                        sampleArticle("a3", "Article 3")));

        mockMvc.perform(get("/api/v1/articles")
                        .param("topic", "AI Healthcare")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listArticles_unknownEmail_defaultsToFree() throws Exception {
        when(tierResolver.resolveTier("unknown@test.com")).thenReturn(SubscriptionTier.FREE);
        when(tierGatingService.archiveDaysFor(SubscriptionTier.FREE)).thenReturn(7);
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare"), eq(7)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/articles")
                        .param("topic", "AI Healthcare")
                        .header("X-Subscriber-Email", "unknown@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(ingestionPort).fetchByTopicWithArchiveLimit("AI Healthcare", 7);
    }
}
