package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
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
 * @version 1.0
 * @since   2026-05-21
 * @updated 2026-05-21
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
                .thenReturn("This is a 3-sentence summary. It covers key themes. More details follow.");

        service.generateSummaries(List.of("AI Healthcare"));

        ArgumentCaptor<TopicSummary> captor = ArgumentCaptor.forClass(TopicSummary.class);
        verify(summaryPort).save(captor.capture());
        TopicSummary saved = captor.getValue();
        assertThat(saved.topic()).isEqualTo("AI Healthcare");
        assertThat(saved.summaryText()).contains("3-sentence summary");
        assertThat(saved.generatedAt()).isNotNull();
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
                .thenReturn("Good summary here. Second sentence. Third sentence.");

        service.generateSummaries(List.of("Failing Topic", "Good Topic"));

        // Good Topic should still be saved even though Failing Topic threw
        ArgumentCaptor<TopicSummary> captor = ArgumentCaptor.forClass(TopicSummary.class);
        verify(summaryPort).save(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("Good Topic");
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
                Instant.now()
        );
    }
}
