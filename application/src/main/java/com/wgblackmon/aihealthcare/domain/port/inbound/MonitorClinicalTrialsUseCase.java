package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for the clinical trial monitoring use case.
 *
 * <p>Provides operations to retrieve clinical trials for display
 * and to trigger an on-demand harvest cycle from ClinicalTrials.gov.
 * The implementation delegates to
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialPort}
 * for persistence and
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialHarvestingPort}
 * for discovery.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
public interface MonitorClinicalTrialsUseCase {

    /**
     * Returns the most recent clinical trials.
     *
     * @param limit maximum number of trials to return
     * @return trials ordered by discoveredAt descending
     */
    List<ClinicalTrial> getRecentTrials(int limit);

    /**
     * Returns recent trials filtered by recruitment status.
     */
    List<ClinicalTrial> getTrialsByStatus(ClinicalTrialStatus status, int limit);

    /**
     * Returns recent trials filtered by phase.
     */
    List<ClinicalTrial> getTrialsByPhase(ClinicalTrialPhase phase, int limit);

    /**
     * Returns a single trial by ID.
     */
    Optional<ClinicalTrial> getTrial(String trialId);

    /**
     * Triggers an on-demand harvest of clinical trials from ClinicalTrials.gov.
     *
     * @return the number of new trials discovered and stored
     */
    int triggerHarvest();
}
