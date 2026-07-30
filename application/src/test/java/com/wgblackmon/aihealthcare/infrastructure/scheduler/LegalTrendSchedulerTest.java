package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectLegalTrendsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LegalTrendScheduler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
class LegalTrendSchedulerTest {

    private DetectLegalTrendsUseCase detectLegalTrendsUseCase;
    private LegalTrendScheduler scheduler;

    @BeforeEach
    void setUp() {
        detectLegalTrendsUseCase = mock(DetectLegalTrendsUseCase.class);
        scheduler = new LegalTrendScheduler(detectLegalTrendsUseCase);
    }

    @Test
    void runsDetectionViaDelegation() {
        LegalTrendSnapshot snapshot = new LegalTrendSnapshot(
                Instant.now(), 30, List.of(), 0);
        when(detectLegalTrendsUseCase.detectLegalTrends()).thenReturn(snapshot);

        scheduler.runWeeklyLegalTrendDetection();

        verify(detectLegalTrendsUseCase).detectLegalTrends();
    }

    @Test
    void swallowsExceptions() {
        doThrow(new RuntimeException("LLM failure"))
                .when(detectLegalTrendsUseCase).detectLegalTrends();

        scheduler.runWeeklyLegalTrendDetection();

        verify(detectLegalTrendsUseCase).detectLegalTrends();
    }
}
