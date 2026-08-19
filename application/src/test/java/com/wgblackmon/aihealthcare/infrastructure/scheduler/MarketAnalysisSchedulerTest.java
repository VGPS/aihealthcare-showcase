package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketAnalysisScheduler}.
 *
 * <p>Verifies delegation to {@link MarketDigestService} and that the
 * scheduler thread survives exceptions thrown by the pipeline.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
class MarketAnalysisSchedulerTest {

    @Mock
    private MarketDigestService marketDigestService;

    @InjectMocks
    private MarketAnalysisScheduler scheduler;

    @Test
    void runDailyDigest_delegatesToMarketDigestService() {
        MarketDigest digest = MarketDigest.empty(LocalDate.now());
        when(marketDigestService.generateDailyDigest(any(LocalDate.class))).thenReturn(digest);

        scheduler.runDailyDigest();

        verify(marketDigestService).generateDailyDigest(any(LocalDate.class));
    }

    @Test
    void runDailyDigest_whenServiceThrows_doesNotPropagateException() {
        doThrow(new RuntimeException("Perplexity API timeout"))
                .when(marketDigestService).generateDailyDigest(any(LocalDate.class));

        // Must not throw — scheduler thread must survive errors
        scheduler.runDailyDigest();
    }
}
