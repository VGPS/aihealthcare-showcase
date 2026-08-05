package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.service.RegulatoryWatchlistMatcher;
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
 * Unit tests for {@link RegulatoryHarvestScheduler}.
 *
 * <p>Verifies the daily harvest orchestration: harvest → dedup → save →
 * watchlist matching → save matches.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class RegulatoryHarvestSchedulerTest {

    private RegulatoryHarvestingPort harvestingPort;
    private RegulatoryEventPort eventPort;
    private WatchlistPort watchlistPort;
    private WatchlistMatchPort watchlistMatchPort;
    private RegulatoryWatchlistMatcher matcher;
    private RegulatoryHarvestScheduler scheduler;

    @BeforeEach
    void setUp() {
        harvestingPort = mock(RegulatoryHarvestingPort.class);
        eventPort = mock(RegulatoryEventPort.class);
        watchlistPort = mock(WatchlistPort.class);
        watchlistMatchPort = mock(WatchlistMatchPort.class);
        matcher = new RegulatoryWatchlistMatcher();
        scheduler = new RegulatoryHarvestScheduler(
                harvestingPort, eventPort, watchlistPort, watchlistMatchPort, matcher, null);
    }

    @Test
    void harvestSavesNewEventsAndMatchesWatchlists() {
        RegulatoryEvent event = event("e1", "K241234");
        when(harvestingPort.harvestAll()).thenReturn(List.of(event));
        when(eventPort.existsByReferenceNumber("K241234")).thenReturn(false);
        when(eventPort.existsBySourceUrl("https://fda.gov/e1")).thenReturn(false);

        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.KEYWORD, "FDA", "FDA", Instant.now());
        when(watchlistPort.findAll()).thenReturn(List.of(item));
        when(watchlistMatchPort.existsByItemAndArticle(anyString(), anyString())).thenReturn(false);

        scheduler.runDailyRegulatoryHarvest();

        verify(eventPort).saveAll(List.of(event));
        verify(watchlistMatchPort).saveAll(anyList());
    }

    @Test
    void harvestSkipsDuplicates() {
        RegulatoryEvent event = event("e1", "K241234");
        when(harvestingPort.harvestAll()).thenReturn(List.of(event));
        when(eventPort.existsByReferenceNumber("K241234")).thenReturn(true);

        scheduler.runDailyRegulatoryHarvest();

        verify(eventPort, never()).saveAll(anyList());
    }

    @Test
    void harvestHandlesEmptyResult() {
        when(harvestingPort.harvestAll()).thenReturn(List.of());

        scheduler.runDailyRegulatoryHarvest();

        verify(eventPort, never()).saveAll(anyList());
        verify(watchlistMatchPort, never()).saveAll(anyList());
    }

    @Test
    void harvestHandlesExceptionGracefully() {
        when(harvestingPort.harvestAll()).thenThrow(new RuntimeException("API failure"));

        // Should not throw
        scheduler.runDailyRegulatoryHarvest();

        verify(eventPort, never()).saveAll(anyList());
    }

    // --- Helper ---

    private RegulatoryEvent event(String id, String refNumber) {
        return new RegulatoryEvent(id, RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "FDA clears AI diagnostic tool",
                "Summary", refNumber, "Applicant", "Device",
                "https://fda.gov/" + id, null, Instant.now(), Instant.now(),
                List.of("AI", "diagnostic"),
                null, null, null, null);
    }
}
