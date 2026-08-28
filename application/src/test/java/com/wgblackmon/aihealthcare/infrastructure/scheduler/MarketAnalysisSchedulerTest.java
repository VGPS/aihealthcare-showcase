package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ReactionHorizon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketAnalysisScheduler}.
 *
 * <p>Verifies delegation to {@link MarketDigestService} and {@link PriceReactionService},
 * and that the scheduler thread survives exceptions thrown by either pipeline.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-28  added runReactionCapture() coverage
 */
@ExtendWith(MockitoExtension.class)
class MarketAnalysisSchedulerTest {

    @Mock
    private MarketDigestService marketDigestService;

    @Mock
    private PriceReactionService priceReactionService;

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

    @Test
    void runReactionCapture_delegatesToPriceReactionService() {
        PriceReactionSnapshot snapshot = new PriceReactionSnapshot(
                "entry-1", "DOCS", ReactionHorizon.ONE_DAY,
                new BigDecimal("10.00"), new BigDecimal("10.80"),
                new BigDecimal("8.00"), Instant.now());
        when(priceReactionService.capturePendingReactions(any(Instant.class)))
                .thenReturn(List.of(snapshot));

        scheduler.runReactionCapture();

        verify(priceReactionService).capturePendingReactions(any(Instant.class));
    }

    @Test
    void runReactionCapture_whenServiceThrows_doesNotPropagateException() {
        doThrow(new RuntimeException("Alpaca API timeout"))
                .when(priceReactionService).capturePendingReactions(any(Instant.class));

        // Must not throw — scheduler thread must survive errors
        scheduler.runReactionCapture();
    }
}
