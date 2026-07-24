package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.service.ClinicalTrialWatchlistMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClinicalTrialHarvestScheduler}.
 *
 * <p>Verifies the daily harvest orchestration: harvest → dedup → save →
 * watchlist matching → save matches.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
class ClinicalTrialHarvestSchedulerTest {

    private ClinicalTrialHarvestingPort harvestingPort;
    private ClinicalTrialPort trialPort;
    private WatchlistPort watchlistPort;
    private WatchlistMatchPort watchlistMatchPort;
    private ClinicalTrialWatchlistMatcher matcher;
    private ClinicalTrialHarvestScheduler scheduler;

    @BeforeEach
    void setUp() {
        harvestingPort = mock(ClinicalTrialHarvestingPort.class);
        trialPort = mock(ClinicalTrialPort.class);
        watchlistPort = mock(WatchlistPort.class);
        watchlistMatchPort = mock(WatchlistMatchPort.class);
        matcher = new ClinicalTrialWatchlistMatcher();
        scheduler = new ClinicalTrialHarvestScheduler(
                harvestingPort, trialPort, watchlistPort, watchlistMatchPort, matcher);
    }

    @Test
    void harvestSavesNewTrialsAndMatchesWatchlists() {
        ClinicalTrial trial = trial("t1", "NCT001", "AI radiology screening trial");
        when(harvestingPort.harvestAll()).thenReturn(List.of(trial));
        when(trialPort.existsByNctId("NCT001")).thenReturn(false);

        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.KEYWORD, "radiology", "radiology", Instant.now());
        when(watchlistPort.findAll()).thenReturn(List.of(item));
        when(watchlistMatchPort.existsByItemAndArticle(anyString(), anyString())).thenReturn(false);

        scheduler.runDailyClinicalTrialHarvest();

        verify(trialPort).saveAll(List.of(trial));
        verify(watchlistMatchPort).saveAll(anyList());
    }

    @Test
    void harvestSkipsDuplicates() {
        ClinicalTrial trial = trial("t1", "NCT001", "AI Trial");
        when(harvestingPort.harvestAll()).thenReturn(List.of(trial));
        when(trialPort.existsByNctId("NCT001")).thenReturn(true);

        scheduler.runDailyClinicalTrialHarvest();

        verify(trialPort, never()).saveAll(anyList());
    }

    @Test
    void harvestHandlesEmptyResult() {
        when(harvestingPort.harvestAll()).thenReturn(List.of());

        scheduler.runDailyClinicalTrialHarvest();

        verify(trialPort, never()).saveAll(anyList());
        verify(watchlistMatchPort, never()).saveAll(anyList());
    }

    @Test
    void harvestHandlesExceptionGracefully() {
        when(harvestingPort.harvestAll()).thenThrow(new RuntimeException("API failure"));

        // Should not throw
        scheduler.runDailyClinicalTrialHarvest();

        verify(trialPort, never()).saveAll(anyList());
    }

    // --- Helper ---

    private ClinicalTrial trial(String trialId, String nctId, String title) {
        return new ClinicalTrial(trialId, nctId, title,
                "Sponsor Inc", ClinicalTrialStatus.RECRUITING, null, null,
                "A study of AI in healthcare",
                "https://clinicaltrials.gov/study/" + nctId,
                "INTERVENTIONAL", null, Instant.now(), List.of("AI", "radiology"));
    }
}
