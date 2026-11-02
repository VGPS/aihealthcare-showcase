package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
import com.wgblackmon.aihealthcare.domain.model.TopicSummaryResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TopicSummaryGenerationService}.
 *
 * <p>All AI and persistence calls are mocked — no real API calls or DB writes.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-21
 * @updated 2026-09-29
 */
@ExtendWith(MockitoExtension.class)
class TopicSummaryGenerationServiceTest {

    @Mock
    private AiSummarizationPort aiPort;

    @Mock
    private TopicSummaryPort summaryPort;

    @Mock
    private ArticleIngestionPort ingestionPort;

    private TopicSummaryGenerationService service;

    @BeforeEach
    void setUp() {
        service = new TopicSummaryGenerationService(aiPort, summaryPort, ingestionPort);
    }

    // -------------------------------------------------------------------------
    // Skip topics with insufficient articles
    // -------------------------------------------------------------------------

    @Test
    void generateSummaries_skipsTopicWithNoArticles() {
        when(ingestionPort.fetchAllByTopic("Empty Topic")).thenReturn(Collections.emptyList());

        service.generateSummaries(List.of("Empty Topic"));

        verify(aiPort, never()).generateTopicSummary(anyString(), anyList());
        verify(summaryPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateSummaries_skipsTopicWithOneArticle() {
        when(ingestionPort.fetchAllByTopic("Single Topic")).thenReturn(List.of(sampleArticle("Article 1")));

        service.generateSummaries(List.of("Single Topic"));

        verify(aiPort, never()).generateTopicSummary(anyString(), anyList());
        verify(summaryPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Happy path — generates and saves
    // -------------------------------------------------------------------------

    @Test
    void generateSummaries_callsAiAndSavesForMultipleArticles() {
        List<NewsArticle> articles = List.of(sampleArticle("Article 1"), sampleArticle("Article 2"));
        when(ingestionPort.fetchAllByTopic("AI Healthcare")).thenReturn(articles);
        when(aiPort.generateTopicSummary(eq("AI Healthcare"), eq(articles)))
                .thenReturn(new TopicSummaryResult(
                        "This is a 3-sentence summary. It covers key themes. More details follow.",
                        "anti-slop-v1", 88));

        service.generateSummaries(List.of("AI Healthcare"));

        ArgumentCaptor<TopicSummary> captor = ArgumentCaptor.forClass(TopicSummary.class);
        verify(summaryPort).save(captor.capture());
        TopicSummary saved = captor.getValue();
        assertThat(saved.topic()).isEqualTo("AI Healthcare");
        assertThat(saved.summaryText()).contains("3-sentence summary");
        assertThat(saved.generatedAt()).isNotNull();
        assertThat(saved.pipelineVersion()).isEqualTo("anti-slop-v1");
        assertThat(saved.lintScore()).isEqualTo(88);
        assertThat(saved.isAntiSlop()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Fault tolerance — AI failure does not stop other topics
    // -------------------------------------------------------------------------

    @Test
    void generateSummaries_continuesWhenAiCallFails() {
        List<NewsArticle> articles = List.of(sampleArticle("A1"), sampleArticle("A2"));
        when(ingestionPort.fetchAllByTopic("Failing Topic")).thenReturn(articles);
        when(ingestionPort.fetchAllByTopic("Good Topic")).thenReturn(articles);
        when(aiPort.generateTopicSummary(eq("Failing Topic"), anyList()))
                .thenThrow(new RuntimeException("AI service down"));
        when(aiPort.generateTopicSummary(eq("Good Topic"), anyList()))
                .thenReturn(new TopicSummaryResult(
                        "Good summary here. Second sentence. Third sentence.",
                        "anti-slop-v1", 85));

        service.generateSummaries(List.of("Failing Topic", "Good Topic"));

        // Good Topic should still be saved even though Failing Topic threw
        ArgumentCaptor<TopicSummary> captor = ArgumentCaptor.forClass(TopicSummary.class);
        verify(summaryPort).save(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("Good Topic");
    }

    // -------------------------------------------------------------------------
    // Freshness gate — skip when no new articles since last summary
    // -------------------------------------------------------------------------

    @Test
    void generateSummaries_skipsTopicWithNoArticlesNewerThanLastSummary() {
        Instant lastSummaryAt = Instant.now().minusSeconds(3600);
        List<NewsArticle> articles = List.of(
                sampleArticleWithPublishedAt("Article 1", lastSummaryAt.minusSeconds(600)),
                sampleArticleWithPublishedAt("Article 2", lastSummaryAt.minusSeconds(300)));
        when(ingestionPort.fetchAllByTopic("Stale Topic")).thenReturn(articles);
        when(summaryPort.findByTopic("Stale Topic")).thenReturn(java.util.Optional.of(
                new TopicSummary("Stale Topic", "Old summary.", lastSummaryAt, "anti-slop-v1", 80)));

        service.generateSummaries(List.of("Stale Topic"));

        verify(aiPort, never()).generateTopicSummary(anyString(), anyList());
        verify(summaryPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateSummaries_regeneratesWhenArticlePublishedAfterLastSummary() {
        Instant lastSummaryAt = Instant.now().minusSeconds(3600);
        List<NewsArticle> articles = List.of(
                sampleArticleWithPublishedAt("Article 1", lastSummaryAt.minusSeconds(600)),
                sampleArticleWithPublishedAt("Article 2", lastSummaryAt.plusSeconds(60)));
        when(ingestionPort.fetchAllByTopic("Fresh Topic")).thenReturn(articles);
        when(summaryPort.findByTopic("Fresh Topic")).thenReturn(java.util.Optional.of(
                new TopicSummary("Fresh Topic", "Old summary.", lastSummaryAt, "anti-slop-v1", 80)));
        when(aiPort.generateTopicSummary(eq("Fresh Topic"), eq(articles)))
                .thenReturn(new TopicSummaryResult(
                        "Updated summary text. Second sentence. Third sentence.",
                        "anti-slop-v1", 90));

        service.generateSummaries(List.of("Fresh Topic"));

        verify(aiPort).generateTopicSummary(eq("Fresh Topic"), eq(articles));
        verify(summaryPort).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateSummaries_regeneratesWhenFreshnessCannotBeDetermined() {
        Instant lastSummaryAt = Instant.now().minusSeconds(3600);
        List<NewsArticle> articles = List.of(
                sampleArticleWithPublishedAt("Article 1", null),
                sampleArticleWithPublishedAt("Article 2", null));
        when(ingestionPort.fetchAllByTopic("Undated Topic")).thenReturn(articles);
        when(summaryPort.findByTopic("Undated Topic")).thenReturn(java.util.Optional.of(
                new TopicSummary("Undated Topic", "Old summary.", lastSummaryAt, "anti-slop-v1", 80)));
        when(aiPort.generateTopicSummary(eq("Undated Topic"), eq(articles)))
                .thenReturn(new TopicSummaryResult(
                        "Updated summary text. Second sentence. Third sentence.",
                        "anti-slop-v1", 90));

        service.generateSummaries(List.of("Undated Topic"));

        verify(aiPort).generateTopicSummary(eq("Undated Topic"), eq(articles));
        verify(summaryPort).save(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Edge case — empty topic list
    // -------------------------------------------------------------------------

    @Test
    void generateSummaries_emptyTopicListNoInteractions() {
        service.generateSummaries(Collections.emptyList());

        verify(ingestionPort, never()).fetchAllByTopic(anyString());
        verify(aiPort, never()).generateTopicSummary(anyString(), anyList());
        verify(summaryPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Test fixture
    // -------------------------------------------------------------------------

    private NewsArticle sampleArticle(String title) {
        return sampleArticleWithPublishedAt(title, Instant.now());
    }

    private NewsArticle sampleArticleWithPublishedAt(String title, Instant publishedAt) {
        return new NewsArticle(
                "id-" + title.hashCode(),
                title,
                URI.create("https://example.com/" + title.hashCode()),
                "Body text for " + title,
                "General AI Healthcare News",
                null,
                1L,
                "Test Source",
                "INDUSTRY",
                0.7,
                publishedAt
        );
    }
}
