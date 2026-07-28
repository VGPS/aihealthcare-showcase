package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSummaryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * <p>Verifies orchestration logic: article retrieval, delegation to
 * {@link TrendDetectionService}, LLM scoring integration, and snapshot
 * persistence via {@link TrendSnapshotPort}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-22
 * @updated 2026-07-28
 */
class TrendOrchestrationServiceTest {

    private ArticleIngestionPort articleIngestionPort;
    private TrendDetectionService trendDetectionService;
    private TrendSnapshotPort trendSnapshotPort;
    private ArticleScoringPort articleScoringPort;
    private TrendSummaryPort trendSummaryPort;
    private TrendOrchestrationService service;
    private TrendOrchestrationService serviceWithScoring;

    @BeforeEach
    void setUp() {
        articleIngestionPort = mock(ArticleIngestionPort.class);
        trendDetectionService = mock(TrendDetectionService.class);
        trendSnapshotPort = mock(TrendSnapshotPort.class);
        articleScoringPort = mock(ArticleScoringPort.class);
        trendSummaryPort = mock(TrendSummaryPort.class);

        // Service with scoring disabled, no summary port
        service = new TrendOrchestrationService(
                articleIngestionPort, trendDetectionService, trendSnapshotPort,
                null, null, false, 7, 5);

        // Service with scoring enabled and summary port available
        serviceWithScoring = new TrendOrchestrationService(
                articleIngestionPort, trendDetectionService, trendSnapshotPort,
                articleScoringPort, trendSummaryPort, true, 7, 5);
    }

    @Test
    void detectTrendsFetchesArticlesAndSavesSnapshot() {
        NewsArticle article = new NewsArticle("a1", "AI in radiology", URI.create("https://example.com"),
                "body", "Topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
        TrendSignal rising = new TrendSignal("radiology", 15, 5, 2, 6.0,
                TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 1);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of(article));
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class))).thenReturn(snapshot);

        TrendSnapshot result = service.detectTrends();

        assertThat(result.risingTopics()).hasSize(1);
        assertThat(result.risingTopics().get(0).keyword()).isEqualTo("radiology");
        verify(articleIngestionPort).fetchRecentArticles(180);
        verify(trendDetectionService).detectTrends(eq(List.of(article)), any(Instant.class));
        verify(trendSnapshotPort).save(any(TrendSnapshot.class));
    }

    @Test
    void detectTrendsHandlesEmptyArticles() {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 0);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of());
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class))).thenReturn(snapshot);

        TrendSnapshot result = service.detectTrends();

        assertThat(result.totalKeywords()).isZero();
        verify(trendSnapshotPort).save(snapshot);
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
    void detectTrends_scoringDisabled_doesNotCallScoringPort() {
        TrendSignal rising = new TrendSignal("radiology", 15, 5, 2, 6.0,
                TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 1);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of());
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class))).thenReturn(snapshot);

        service.detectTrends();

        verify(articleScoringPort, never()).scoreArticles(anyList(), anyString(), anyString(), anyInt());
    }

    @Test
    void detectTrends_scoringEnabled_callsScoringPortForRisingKeywords() {
        NewsArticle article = new NewsArticle("a1", "Radiology AI breakthrough",
                URI.create("https://example.com"), "body about radiology", "Topic",
                null, null, "Source", "INDUSTRY", 0.5, Instant.now());
        TrendSignal rising = new TrendSignal("radiology", 15, 5, 2, 6.0,
                TrendDirection.RISING, Instant.now());
        TrendSnapshot baseSnapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 1);

        ScoredArticle scored = new ScoredArticle("a1", "Radiology AI breakthrough", 8,
                "Major FDA clearance for AI radiology tool", "radiology");

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of(article));
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class)))
                .thenReturn(baseSnapshot);
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class), any(Map.class)))
                .thenReturn(baseSnapshot);
        when(articleScoringPort.scoreArticles(anyList(), eq("radiology"), anyString(), eq(7)))
                .thenReturn(List.of(scored));

        serviceWithScoring.detectTrends();

        verify(articleScoringPort).scoreArticles(anyList(), eq("radiology"), anyString(), eq(7));
    }

    @Test
    void detectTrends_scoringEnabled_noRisingTopics_skipsScoringPort() {
        TrendSnapshot emptySnapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 0);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of());
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class)))
                .thenReturn(emptySnapshot);

        serviceWithScoring.detectTrends();

        verify(articleScoringPort, never()).scoreArticles(anyList(), anyString(), anyString(), anyInt());
    }

    @Test
    void detectTrends_summaryPortAvailable_generatesSummaries() {
        NewsArticle article = new NewsArticle("a1", "Radiology AI breakthrough",
                URI.create("https://example.com"), "body about radiology", "Topic",
                null, null, "Source", "INDUSTRY", 0.5, Instant.now());
        TrendSignal rising = new TrendSignal("radiology", 15, 5, 2, 6.0,
                TrendDirection.RISING, Instant.now());
        TrendSnapshot baseSnapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 1);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of(article));
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class)))
                .thenReturn(baseSnapshot);
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class), any(Map.class)))
                .thenReturn(baseSnapshot);
        when(articleScoringPort.scoreArticles(anyList(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(trendSummaryPort.isAvailable()).thenReturn(true);
        when(trendSummaryPort.generateSummary(eq("radiology"), anyList()))
                .thenReturn("Deep research summary about radiology AI.");

        TrendSnapshot result = serviceWithScoring.detectTrends();

        verify(trendSummaryPort).generateSummary(eq("radiology"), anyList());
        assertThat(result.risingTopics().get(0).summary())
                .isEqualTo("Deep research summary about radiology AI.");
    }

    @Test
    void detectTrends_summaryPortUnavailable_skipsSummaries() {
        TrendSignal rising = new TrendSignal("radiology", 15, 5, 2, 6.0,
                TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 1);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of());
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class)))
                .thenReturn(snapshot);
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class), any(Map.class)))
                .thenReturn(snapshot);
        when(trendSummaryPort.isAvailable()).thenReturn(false);

        serviceWithScoring.detectTrends();

        verify(trendSummaryPort, never()).generateSummary(anyString(), anyList());
    }

    @Test
    void detectTrends_respectsMaxSummariesPerRun() {
        NewsArticle a1 = new NewsArticle("a1", "Article about radiology",
                URI.create("https://example.com/1"), "body radiology", "T",
                null, null, "S", "INDUSTRY", 0.5, Instant.now());
        NewsArticle a2 = new NewsArticle("a2", "Article about genomics",
                URI.create("https://example.com/2"), "body genomics", "T",
                null, null, "S", "INDUSTRY", 0.5, Instant.now());

        TrendSignal s1 = new TrendSignal("radiology", 15, 5, 2, 6.0,
                TrendDirection.RISING, Instant.now());
        TrendSignal s2 = new TrendSignal("genomics", 12, 4, 1, 5.0,
                TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(s1, s2), List.of(), List.of(), 2);

        // Service with max 1 summary per run
        TrendOrchestrationService limitedService = new TrendOrchestrationService(
                articleIngestionPort, trendDetectionService, trendSnapshotPort,
                articleScoringPort, trendSummaryPort, true, 7, 1);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of(a1, a2));
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class)))
                .thenReturn(snapshot);
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class), any(Map.class)))
                .thenReturn(snapshot);
        when(articleScoringPort.scoreArticles(anyList(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(trendSummaryPort.isAvailable()).thenReturn(true);
        when(trendSummaryPort.generateSummary(anyString(), anyList()))
                .thenReturn("Summary text.");

        limitedService.detectTrends();

        // Only 1 summary generated despite 2 rising keywords
        verify(trendSummaryPort, times(1)).generateSummary(anyString(), anyList());
    }
}
