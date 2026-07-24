package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialPort;
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
 * Unit tests for {@link ClinicalTrialService}.
 *
 * <p>Verifies harvest orchestration (deduplication, persistence) and
 * delegation of retrieval methods to {@link ClinicalTrialPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
class ClinicalTrialServiceTest {

    private ClinicalTrialPort trialPort;
    private ClinicalTrialHarvestingPort harvestingPort;
    private ClinicalTrialService service;

    @BeforeEach
    void setUp() {
        trialPort = mock(ClinicalTrialPort.class);
        harvestingPort = mock(ClinicalTrialHarvestingPort.class);
        service = new ClinicalTrialService(trialPort, harvestingPort);
    }

    @Test
    void triggerHarvestSavesNewTrials() {
        ClinicalTrial trial = trial("t1", "NCT001");
        when(harvestingPort.harvestAll()).thenReturn(List.of(trial));
        when(trialPort.existsByNctId("NCT001")).thenReturn(false);

        int result = service.triggerHarvest();

        assertThat(result).isEqualTo(1);
        verify(trialPort).saveAll(List.of(trial));
    }

    @Test
    void triggerHarvestFiltersDuplicatesByNctId() {
        ClinicalTrial trial = trial("t1", "NCT001");
        when(harvestingPort.harvestAll()).thenReturn(List.of(trial));
        when(trialPort.existsByNctId("NCT001")).thenReturn(true);

        int result = service.triggerHarvest();

        assertThat(result).isEqualTo(0);
        verify(trialPort, never()).saveAll(anyList());
    }

    @Test
    void triggerHarvestMixedNewAndDuplicate() {
        ClinicalTrial newTrial = trial("t1", "NCT001");
        ClinicalTrial dupTrial = trial("t2", "NCT002");
        when(harvestingPort.harvestAll()).thenReturn(List.of(newTrial, dupTrial));
        when(trialPort.existsByNctId("NCT001")).thenReturn(false);
        when(trialPort.existsByNctId("NCT002")).thenReturn(true);

        int result = service.triggerHarvest();

        assertThat(result).isEqualTo(1);
        verify(trialPort).saveAll(List.of(newTrial));
    }

    @Test
    void triggerHarvestEmptyHarvestReturnsZero() {
        when(harvestingPort.harvestAll()).thenReturn(List.of());

        int result = service.triggerHarvest();

        assertThat(result).isEqualTo(0);
        verify(trialPort, never()).saveAll(anyList());
    }

    @Test
    void getRecentTrialsDelegatesToPort() {
        List<ClinicalTrial> expected = List.of(trial("t1", "NCT001"));
        when(trialPort.findRecent(10)).thenReturn(expected);

        List<ClinicalTrial> result = service.getRecentTrials(10);

        assertThat(result).isEqualTo(expected);
        verify(trialPort).findRecent(10);
    }

    @Test
    void getTrialsByStatusDelegatesToPort() {
        List<ClinicalTrial> expected = List.of(trial("t1", "NCT001"));
        when(trialPort.findByStatus(ClinicalTrialStatus.RECRUITING, 10)).thenReturn(expected);

        List<ClinicalTrial> result = service.getTrialsByStatus(ClinicalTrialStatus.RECRUITING, 10);

        assertThat(result).isEqualTo(expected);
        verify(trialPort).findByStatus(ClinicalTrialStatus.RECRUITING, 10);
    }

    @Test
    void getTrialsByPhaseDelegatesToPort() {
        List<ClinicalTrial> expected = List.of(trial("t1", "NCT001"));
        when(trialPort.findByPhase(ClinicalTrialPhase.PHASE_3, 10)).thenReturn(expected);

        List<ClinicalTrial> result = service.getTrialsByPhase(ClinicalTrialPhase.PHASE_3, 10);

        assertThat(result).isEqualTo(expected);
        verify(trialPort).findByPhase(ClinicalTrialPhase.PHASE_3, 10);
    }

    @Test
    void getTrialDelegatesToPort() {
        ClinicalTrial trial = trial("t1", "NCT001");
        when(trialPort.findById("t1")).thenReturn(Optional.of(trial));

        Optional<ClinicalTrial> result = service.getTrial("t1");

        assertThat(result).isPresent().contains(trial);
        verify(trialPort).findById("t1");
    }

    @Test
    void getTrialReturnsEmptyWhenNotFound() {
        when(trialPort.findById("missing")).thenReturn(Optional.empty());

        Optional<ClinicalTrial> result = service.getTrial("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void triggerHarvestHandlesMultipleNewTrials() {
        ClinicalTrial t1 = trial("t1", "NCT001");
        ClinicalTrial t2 = trial("t2", "NCT002");
        ClinicalTrial t3 = trial("t3", "NCT003");
        when(harvestingPort.harvestAll()).thenReturn(List.of(t1, t2, t3));
        when(trialPort.existsByNctId("NCT001")).thenReturn(false);
        when(trialPort.existsByNctId("NCT002")).thenReturn(false);
        when(trialPort.existsByNctId("NCT003")).thenReturn(false);

        int result = service.triggerHarvest();

        assertThat(result).isEqualTo(3);
        verify(trialPort).saveAll(List.of(t1, t2, t3));
    }

    // --- Helper ---

    private ClinicalTrial trial(String trialId, String nctId) {
        return new ClinicalTrial(trialId, nctId, "AI Trial " + nctId,
                null, ClinicalTrialStatus.RECRUITING, null, null, null,
                "https://clinicaltrials.gov/study/" + nctId, null, null,
                Instant.now(), null);
    }
}
