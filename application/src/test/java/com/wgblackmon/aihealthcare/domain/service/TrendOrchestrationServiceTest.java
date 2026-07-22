package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrendOrchestrationService}.
 *
 * <p>Verifies orchestration logic: article retrieval, delegation to
 * {@link TrendDetectionService}, and snapshot persistence via
 * {@link TrendSnapshotPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class TrendOrchestrationServiceTest {

    private ArticleIngestionPort articleIngestionPort;
    private TrendDetectionService trendDetectionService;
    private TrendSnapshotPort trendSnapshotPort;
    private TrendOrchestrationService service;

    @BeforeEach
    void setUp() {
        articleIngestionPort = mock(ArticleIngestionPort.class);
        trendDetectionService = mock(TrendDetectionService.class);
        trendSnapshotPort = mock(TrendSnapshotPort.class);
        service = new TrendOrchestrationService(
                articleIngestionPort, trendDetectionService, trendSnapshotPort);
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
}
