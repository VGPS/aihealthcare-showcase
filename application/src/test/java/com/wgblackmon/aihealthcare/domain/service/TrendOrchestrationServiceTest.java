package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ExtractedTrend;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSummaryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendTopicExtractionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrendOrchestrationService}.
 *
 * <p>Verifies orchestration logic: LLM-based topic extraction, momentum
 * comparison, article attachment, scoring, summary generation, and snapshot
 * persistence.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-22
 * @updated 2026-07-29
 */
class TrendOrchestrationServiceTest {

    private ArticleIngestionPort articleIngestionPort;
    private TrendTopicExtractionPort trendTopicExtractionPort;
    private TrendSnapshotPort trendSnapshotPort;
    private ArticleScoringPort articleScoringPort;
    private TrendSummaryPort trendSummaryPort;
    private TrendOrchestrationService service;
    private TrendOrchestrationService serviceWithScoring;

    @BeforeEach
    void setUp() {
        articleIngestionPort = mock(ArticleIngestionPort.class);
        trendTopicExtractionPort = mock(TrendTopicExtractionPort.class);
        trendSnapshotPort = mock(TrendSnapshotPort.class);
        articleScoringPort = mock(ArticleScoringPort.class);
        trendSummaryPort = mock(TrendSummaryPort.class);

        // Service with scoring disabled, no summary port
        service = new TrendOrchestrationService(
                articleIngestionPort, trendTopicExtractionPort, trendSnapshotPort,
                null, null, false, 7, 5, 20);

        // Service with scoring enabled and summary port available
        serviceWithScoring = new TrendOrchestrationService(
                articleIngestionPort, trendTopicExtractionPort, trendSnapshotPort,
                articleScoringPort, trendSummaryPort, true, 7, 5, 20);
    }

    @Test
    void detectTrendsExtractsTopicsViaLlmAndSavesSnapshot() {
        Instant now = Instant.now();
        NewsArticle recentArticle = new NewsArticle("a1", "AI in radiology breakthrough",
                URI.create("https://example.com"), "body", "Topic",
                null, null, "Source", "INDUSTRY", 0.5, now);
        NewsArticle priorArticle = new NewsArticle("a2", "Earlier radiology study",
                URI.create("https://example.com/2"), "body", "Topic",
                null, null, "Source", "ACADEMIC", 0.9, now.minus(60, ChronoUnit.DAYS));

        ExtractedTrend recentTrend = new ExtractedTrend(
                "Radiology AI", "Significant advances in radiology AI tools",
                List.of(0));
        ExtractedTrend priorTrend = new ExtractedTrend(
                "Radiology AI", "Earlier radiology research", List.of(0));

        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(recentArticle));
        when(articleIngestionPort.fetchRecentArticles(90)).thenReturn(List.of(recentArticle, priorArticle));
        when(trendTopicExtractionPort.extractTopics(eq(List.of("AI in radiology breakthrough")), eq(20)))
                .thenReturn(List.of(recentTrend));
        when(trendTopicExtractionPort.extractTopics(eq(List.of("Earlier radiology study")), eq(20)))
                .thenReturn(List.of(priorTrend));

        TrendSnapshot result = service.detectTrends();

        assertThat(result.risingTopics()).isNotEmpty();
        verify(trendTopicExtractionPort, times(2)).extractTopics(anyList(), eq(20));
        verify(trendSnapshotPort).save(any(TrendSnapshot.class));
    }

    @Test
    void detectTrendsHandlesEmptyArticles() {
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of());
        when(articleIngestionPort.fetchRecentArticles(90)).thenReturn(List.of());

        TrendSnapshot result = service.detectTrends();

        assertThat(result.totalKeywords()).isZero();
        assertThat(result.risingTopics()).isEmpty();
        verify(trendSnapshotPort).save(any(TrendSnapshot.class));
    }

    @Test
    void getLatestSnapshotDelegatesToPort() {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 5);
        when(trendSnapshotPort.findLatest()).thenReturn(Optional.of(snapshot));

        Optional<TrendSnapshot> result = service.getLatestSnapshot();

        assertThat(result).isPresent().containsSame(snapshot);
        verify(trendSnapshotPort).findLatest();
    }

    @Test
    void getLatestSnapshotReturnsEmptyWhenNoneExists() {
        when(trendSnapshotPort.findLatest()).thenReturn(Optional.empty());

        Optional<TrendSnapshot> result = service.getLatestSnapshot();

        assertThat(result).isEmpty();
    }

    @Test
    void newThemesClassifiedAsNew() {
        Instant now = Instant.now();
        NewsArticle recentArticle = new NewsArticle("a1", "Novel ambient AI launch",
                URI.create("https://example.com"), "body", "Topic",
                null, null, "Source", "INDUSTRY", 0.5, now);

        ExtractedTrend recentTrend = new ExtractedTrend(
                "Ambient AI", "New ambient documentation products launching",
                List.of(0));

        // Recent window has a theme, prior window has nothing
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(recentArticle));
        when(articleIngestionPort.fetchRecentArticles(90)).thenReturn(List.of(recentArticle));
        when(trendTopicExtractionPort.extractTopics(eq(List.of("Novel ambient AI launch")), eq(20)))
                .thenReturn(List.of(recentTrend));

        TrendSnapshot result = service.detectTrends();

        // Should be classified as NEW since no prior-window articles (all articles are recent)
        assertThat(result.risingTopics()).isNotEmpty();
        assertThat(result.risingTopics().get(0).keyword()).isEqualTo("Ambient AI");
        assertThat(result.risingTopics().get(0).direction()).isEqualTo(TrendDirection.NEW);
    }

    @Test
    void detectTrends_scoringDisabled_doesNotCallScoringPort() {
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of());
        when(articleIngestionPort.fetchRecentArticles(90)).thenReturn(List.of());

        service.detectTrends();

        verify(articleScoringPort, never()).scoreArticles(anyList(), anyString(), anyString(), anyInt());
    }

    @Test
    void detectTrends_summaryPortAvailable_generatesSummaries() {
        Instant now = Instant.now();
        NewsArticle article = new NewsArticle("a1", "Radiology AI breakthrough",
                URI.create("https://example.com"), "body about radiology", "Topic",
                null, null, "Source", "INDUSTRY", 0.5, now);

        ExtractedTrend trend = new ExtractedTrend(
                "Radiology AI", "Advances in radiology AI", List.of(0));

        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(article));
        when(articleIngestionPort.fetchRecentArticles(90)).thenReturn(List.of(article));
        when(trendTopicExtractionPort.extractTopics(anyList(), eq(20)))
                .thenReturn(List.of(trend));
        when(trendSummaryPort.isAvailable()).thenReturn(true);
        when(trendSummaryPort.generateSummary(eq("Radiology AI"), anyList()))
                .thenReturn("Deep research summary about radiology AI.");

        TrendSnapshot result = serviceWithScoring.detectTrends();

        verify(trendSummaryPort).generateSummary(eq("Radiology AI"), anyList());
        assertThat(result.risingTopics().get(0).summary())
                .isEqualTo("Deep research summary about radiology AI.");
    }

    @Test
    void detectTrends_summaryPortUnavailable_skipsSummaries() {
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of());
        when(articleIngestionPort.fetchRecentArticles(90)).thenReturn(List.of());
        when(trendSummaryPort.isAvailable()).thenReturn(false);

        serviceWithScoring.detectTrends();

        verify(trendSummaryPort, never()).generateSummary(anyString(), anyList());
    }

    @Test
    void detectTrends_respectsMaxSummariesPerRun() {
        Instant now = Instant.now();
        NewsArticle a1 = new NewsArticle("a1", "Article about radiology",
                URI.create("https://example.com/1"), "body radiology", "T",
                null, null, "S", "INDUSTRY", 0.5, now);
        NewsArticle a2 = new NewsArticle("a2", "Article about genomics",
                URI.create("https://example.com/2"), "body genomics", "T",
                null, null, "S", "INDUSTRY", 0.5, now);

        ExtractedTrend t1 = new ExtractedTrend("Radiology", "Radiology trend", List.of(0));
        ExtractedTrend t2 = new ExtractedTrend("Genomics", "Genomics trend", List.of(1));

        // Service with max 1 summary per run
        TrendOrchestrationService limitedService = new TrendOrchestrationService(
                articleIngestionPort, trendTopicExtractionPort, trendSnapshotPort,
                articleScoringPort, trendSummaryPort, false, 7, 1, 20);

        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(a1, a2));
        when(articleIngestionPort.fetchRecentArticles(90)).thenReturn(List.of(a1, a2));
        when(trendTopicExtractionPort.extractTopics(anyList(), eq(20)))
                .thenReturn(List.of(t1, t2));
        when(trendSummaryPort.isAvailable()).thenReturn(true);
        when(trendSummaryPort.generateSummary(anyString(), anyList()))
                .thenReturn("Summary text.");

        limitedService.detectTrends();

        // Only 1 summary generated despite 2 rising topics
        verify(trendSummaryPort, times(1)).generateSummary(anyString(), anyList());
    }

    @Test
    void detectTrends_attachesArticlesToSignals() {
        Instant now = Instant.now();
        NewsArticle article = new NewsArticle("a1", "FDA clears radiology AI tool",
                URI.create("https://example.com"), "Radiology AI body", "Topic",
                null, null, "Source", "INDUSTRY", 0.5, now);

        ExtractedTrend trend = new ExtractedTrend(
                "FDA Clearances", "Rising FDA clearances for AI tools",
                List.of(0));

        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(article));
        when(articleIngestionPort.fetchRecentArticles(90)).thenReturn(List.of(article));
        when(trendTopicExtractionPort.extractTopics(anyList(), eq(20)))
                .thenReturn(List.of(trend));

        TrendSnapshot result = service.detectTrends();

        assertThat(result.risingTopics()).hasSize(1);
        assertThat(result.risingTopics().get(0).topArticles()).isNotEmpty();
        assertThat(result.risingTopics().get(0).topArticles().get(0).title())
                .isEqualTo("FDA clears radiology AI tool");
    }
}
