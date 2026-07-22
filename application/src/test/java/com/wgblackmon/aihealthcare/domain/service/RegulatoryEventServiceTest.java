package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryHarvestingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegulatoryEventService}.
 *
 * <p>Verifies harvest orchestration (deduplication, persistence) and
 * delegation of retrieval methods to {@link RegulatoryEventPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class RegulatoryEventServiceTest {

    private RegulatoryEventPort eventPort;
    private RegulatoryHarvestingPort harvestingPort;
    private RegulatoryEventService service;

    @BeforeEach
    void setUp() {
        eventPort = mock(RegulatoryEventPort.class);
        harvestingPort = mock(RegulatoryHarvestingPort.class);
        service = new RegulatoryEventService(eventPort, harvestingPort);
    }

    @Test
    void triggerHarvestSavesNewEvents() {
        RegulatoryEvent event = event("e1", "K241234", "https://fda.gov/e1");
        when(harvestingPort.harvestAll()).thenReturn(List.of(event));
        when(eventPort.existsByReferenceNumber("K241234")).thenReturn(false);
        when(eventPort.existsBySourceUrl("https://fda.gov/e1")).thenReturn(false);

        int count = service.triggerHarvest();

        assertThat(count).isEqualTo(1);
        verify(eventPort).saveAll(List.of(event));
    }

    @Test
    void triggerHarvestFiltersDuplicatesByReferenceNumber() {
        RegulatoryEvent event = event("e1", "K241234", "https://fda.gov/e1");
        when(harvestingPort.harvestAll()).thenReturn(List.of(event));
        when(eventPort.existsByReferenceNumber("K241234")).thenReturn(true);

        int count = service.triggerHarvest();

        assertThat(count).isZero();
        verify(eventPort, never()).saveAll(anyList());
    }

    @Test
    void triggerHarvestFiltersDuplicatesBySourceUrl() {
        RegulatoryEvent event = event("e1", null, "https://fda.gov/e1");
        when(harvestingPort.harvestAll()).thenReturn(List.of(event));
        when(eventPort.existsBySourceUrl("https://fda.gov/e1")).thenReturn(true);

        int count = service.triggerHarvest();

        assertThat(count).isZero();
        verify(eventPort, never()).saveAll(anyList());
    }

    @Test
    void triggerHarvestHandlesEmptyHarvest() {
        when(harvestingPort.harvestAll()).thenReturn(List.of());

        int count = service.triggerHarvest();

        assertThat(count).isZero();
        verify(eventPort, never()).saveAll(anyList());
    }

    @Test
    void triggerHarvestMixOfNewAndDuplicate() {
        RegulatoryEvent existing = event("e1", "K241234", "https://fda.gov/e1");
        RegulatoryEvent fresh = event("e2", "K241999", "https://fda.gov/e2");
        when(harvestingPort.harvestAll()).thenReturn(List.of(existing, fresh));
        when(eventPort.existsByReferenceNumber("K241234")).thenReturn(true);
        when(eventPort.existsByReferenceNumber("K241999")).thenReturn(false);
        when(eventPort.existsBySourceUrl("https://fda.gov/e2")).thenReturn(false);

        int count = service.triggerHarvest();

        assertThat(count).isEqualTo(1);
        verify(eventPort).saveAll(List.of(fresh));
    }

    @Test
    void getRecentEventsDelegatesToPort() {
        RegulatoryEvent event = event("e1", "K241234", "https://fda.gov/e1");
        when(eventPort.findRecent(10)).thenReturn(List.of(event));

        List<RegulatoryEvent> result = service.getRecentEvents(10);

        assertThat(result).hasSize(1);
        verify(eventPort).findRecent(10);
    }

    @Test
    void getEventsByTypeDelegatesToPort() {
        when(eventPort.findByType(RegulatoryEventType.FDA_510K_CLEARANCE, 5))
                .thenReturn(List.of());

        List<RegulatoryEvent> result = service.getEventsByType(
                RegulatoryEventType.FDA_510K_CLEARANCE, 5);

        assertThat(result).isEmpty();
        verify(eventPort).findByType(RegulatoryEventType.FDA_510K_CLEARANCE, 5);
    }

    @Test
    void getEventsByBodyDelegatesToPort() {
        when(eventPort.findByBody(RegulatoryBody.CMS, 5)).thenReturn(List.of());

        List<RegulatoryEvent> result = service.getEventsByBody(RegulatoryBody.CMS, 5);

        assertThat(result).isEmpty();
        verify(eventPort).findByBody(RegulatoryBody.CMS, 5);
    }

    @Test
    void getEventDelegatesToPort() {
        RegulatoryEvent event = event("e1", "K241234", "https://fda.gov/e1");
        when(eventPort.findById("e1")).thenReturn(Optional.of(event));

        Optional<RegulatoryEvent> result = service.getEvent("e1");

        assertThat(result).isPresent();
        assertThat(result.get().eventId()).isEqualTo("e1");
    }

    @Test
    void getEventReturnsEmptyWhenNotFound() {
        when(eventPort.findById("missing")).thenReturn(Optional.empty());

        Optional<RegulatoryEvent> result = service.getEvent("missing");

        assertThat(result).isEmpty();
    }

    // --- Helper ---

    private RegulatoryEvent event(String id, String refNumber, String sourceUrl) {
        return new RegulatoryEvent(id, RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "510(k) Clearance",
                "Summary", refNumber, "Applicant Inc", "AI Device",
                sourceUrl, null, Instant.now(), Instant.now(),
                List.of("AI", "radiology"));
    }
}
