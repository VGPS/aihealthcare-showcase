package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CompositeRegulatoryHarvester}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class CompositeRegulatoryHarvesterTest {

    @Test
    void aggregatesFromMultipleSources() {
        RegulatorySourceHarvester source1 = mock(RegulatorySourceHarvester.class);
        RegulatorySourceHarvester source2 = mock(RegulatorySourceHarvester.class);
        when(source1.sourceName()).thenReturn("Source1");
        when(source2.sourceName()).thenReturn("Source2");
        when(source1.harvest(anyInt(), anyList())).thenReturn(List.of(event("e1")));
        when(source2.harvest(anyInt(), anyList())).thenReturn(List.of(event("e2")));

        RegulatoryHarvestProperties props = new RegulatoryHarvestProperties();
        CompositeRegulatoryHarvester composite = new CompositeRegulatoryHarvester(
                List.of(source1, source2), props);

        List<RegulatoryEvent> result = composite.harvestAll();

        assertThat(result).hasSize(2);
        verify(source1).harvest(7, props.getAiKeywords());
        verify(source2).harvest(7, props.getAiKeywords());
    }

    @Test
    void returnsEmptyWhenDisabled() {
        RegulatoryHarvestProperties props = new RegulatoryHarvestProperties();
        props.setEnabled(false);

        CompositeRegulatoryHarvester composite = new CompositeRegulatoryHarvester(List.of(), props);

        List<RegulatoryEvent> result = composite.harvestAll();

        assertThat(result).isEmpty();
    }

    @Test
    void continuesOnSourceFailure() {
        RegulatorySourceHarvester failing = mock(RegulatorySourceHarvester.class);
        RegulatorySourceHarvester working = mock(RegulatorySourceHarvester.class);
        when(failing.sourceName()).thenReturn("Failing");
        when(working.sourceName()).thenReturn("Working");
        when(failing.harvest(anyInt(), anyList())).thenThrow(new RuntimeException("API down"));
        when(working.harvest(anyInt(), anyList())).thenReturn(List.of(event("e1")));

        RegulatoryHarvestProperties props = new RegulatoryHarvestProperties();
        CompositeRegulatoryHarvester composite = new CompositeRegulatoryHarvester(
                List.of(failing, working), props);

        List<RegulatoryEvent> result = composite.harvestAll();

        assertThat(result).hasSize(1);
    }

    private RegulatoryEvent event(String id) {
        return new RegulatoryEvent(id, RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "Event " + id, null, null, null, null,
                "https://fda.gov/" + id, null, null, Instant.now(), null,
                null, null, null, null);
    }
}
