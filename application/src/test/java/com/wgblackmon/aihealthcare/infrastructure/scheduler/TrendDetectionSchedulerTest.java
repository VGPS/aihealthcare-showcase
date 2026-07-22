package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.service.TrendDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrendDetectionScheduler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class TrendDetectionSchedulerTest {

    private ArticleIngestionPort articleIngestionPort;
    private TrendDetectionService trendDetectionService;
    private TrendSnapshotPort trendSnapshotPort;
    private TrendDetectionScheduler scheduler;

    @BeforeEach
    void setUp() {
        articleIngestionPort = mock(ArticleIngestionPort.class);
        trendDetectionService = mock(TrendDetectionService.class);
        trendSnapshotPort = mock(TrendSnapshotPort.class);
        scheduler = new TrendDetectionScheduler(
                articleIngestionPort, trendDetectionService, trendSnapshotPort);
    }

    @Test
    void runsDetectionAndSavesSnapshot() {
        NewsArticle article = new NewsArticle("a1", "Test title", URI.create("https://example.com"),
                null, "Topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 0);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of(article));
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class))).thenReturn(snapshot);

        scheduler.runWeeklyTrendDetection();

        verify(articleIngestionPort).fetchRecentArticles(180);
        verify(trendDetectionService).detectTrends(anyList(), any(Instant.class));
        verify(trendSnapshotPort).save(snapshot);
    }

    @Test
    void handlesEmptyArticles() {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 0);

        when(articleIngestionPort.fetchRecentArticles(180)).thenReturn(List.of());
        when(trendDetectionService.detectTrends(anyList(), any(Instant.class))).thenReturn(snapshot);

        scheduler.runWeeklyTrendDetection();

        verify(trendSnapshotPort).save(snapshot);
    }

    @Test
    void swallowsExceptions() {
        when(articleIngestionPort.fetchRecentArticles(anyInt()))
                .thenThrow(new RuntimeException("DB failure"));

        scheduler.runWeeklyTrendDetection();

        verify(trendSnapshotPort, never()).save(any());
    }
}
