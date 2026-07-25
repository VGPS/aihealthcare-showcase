package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
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
 * @updated 2026-07-24
 */
class TrendOrchestrationServiceTest {

    private ArticleIngestionPort articleIngestionPort;
    private TrendDetectionService trendDetectionService;
    private TrendSnapshotPort trendSnapshotPort;
    private ArticleScoringPort articleScoringPort;
    private TrendOrchestrationService service;
    private TrendOrchestrationService serviceWithScoring;

    @BeforeEach
    void setUp() {
        articleIngestionPort = mock(ArticleIngestionPort.class);
        trendDetectionService = mock(TrendDetectionService.class);
        trendSnapshotPort = mock(TrendSnapshotPort.class);
        articleScoringPort = mock(ArticleScoringPort.class);

        // Service with scoring disabled (backward-compatible)
        service = new TrendOrchestrationService(
                articleIngestionPort, trendDetectionService, trendSnapshotPort,
                null, false, 7);

        // Service with scoring enabled
        serviceWithScoring = new TrendOrchestrationService(
                articleIngestionPort, trendDetectionService, trendSnapshotPort,
                articleScoringPort, true, 7);
    }

    @Test
    void detectTrendsFetchesArticlesAndSavesSnapshot() {
        NewsArticle article = new NewsArticle("a1", "AI in radiology", URI.create("https://example.com"),
                "body", "Topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
        TrendSignal rising = new TrendSignal("radiology", 15, 5, 2, 6.0,
                TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 1);

        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of(article));
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class))).thenReturn(snapshot);

        TrendSnapshot result = service.detectTrends();

        assertThat(result).isSameAs(snapshot);
        verify(articleIngestionPort).fetchRecentArticles(7);
        verify(trendDetectionService).detectTrends(eq(List.of(article)), any(Instant.class));
        verify(trendSnapshotPort).save(snapshot);
    }

    @Test
    void detectTrendsHandlesEmptyArticles() {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 0);

        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of());
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

        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of());
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

        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of(article));
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

        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of());
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class)))
                .thenReturn(emptySnapshot);

        serviceWithScoring.detectTrends();

        verify(articleScoringPort, never()).scoreArticles(anyList(), anyString(), anyString(), anyInt());
    }
}
