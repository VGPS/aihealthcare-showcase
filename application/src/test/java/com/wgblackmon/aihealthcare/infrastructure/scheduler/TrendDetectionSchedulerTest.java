package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrendDetectionScheduler}.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-22
 * @updated 2026-07-29
 */
class TrendDetectionSchedulerTest {

    private DetectTrendsUseCase detectTrendsUseCase;
    private TrendDetectionScheduler scheduler;

    @BeforeEach
    void setUp() {
        detectTrendsUseCase = mock(DetectTrendsUseCase.class);
        scheduler = new TrendDetectionScheduler(detectTrendsUseCase);
    }

    @Test
    void runsDetectionViaDelegation() {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 0);
        when(detectTrendsUseCase.detectTrends()).thenReturn(snapshot);

        scheduler.runWeeklyTrendDetection();

        verify(detectTrendsUseCase).detectTrends();
    }

    @Test
    void swallowsExceptions() {
        doThrow(new RuntimeException("LLM failure"))
                .when(detectTrendsUseCase).detectTrends();

        scheduler.runWeeklyTrendDetection();

        verify(detectTrendsUseCase).detectTrends();
    }
}
