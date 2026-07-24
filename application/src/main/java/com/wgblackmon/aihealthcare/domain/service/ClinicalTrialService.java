package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorClinicalTrialsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application-layer service implementing the clinical trial monitoring use case.
 *
 * <p>Orchestrates harvest, deduplication by NCT ID, and persistence of
 * clinical trials. Retrieval methods delegate directly to
 * {@link ClinicalTrialPort}.
 *
 * <p>This class carries no Spring annotations — it is wired as a {@code @Bean}
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@Slf4j
public class ClinicalTrialService implements MonitorClinicalTrialsUseCase {

    private final ClinicalTrialPort clinicalTrialPort;
    private final ClinicalTrialHarvestingPort clinicalTrialHarvestingPort;

    public ClinicalTrialService(ClinicalTrialPort clinicalTrialPort,
                                 ClinicalTrialHarvestingPort clinicalTrialHarvestingPort) {
        log.debug("ClinicalTrialService() | clinicalTrialPort={}, clinicalTrialHarvestingPort={}",
                  clinicalTrialPort, clinicalTrialHarvestingPort);
        this.clinicalTrialPort = clinicalTrialPort;
        this.clinicalTrialHarvestingPort = clinicalTrialHarvestingPort;
    }

    @Override
    public List<ClinicalTrial> getRecentTrials(int limit) {
        log.debug("getRecentTrials() | limit={}", limit);

        List<ClinicalTrial> result = clinicalTrialPort.findRecent(limit);

        log.debug("getRecentTrials() | return={} trials", result.size());
        return result;
    }

    @Override
    public List<ClinicalTrial> getTrialsByStatus(ClinicalTrialStatus status, int limit) {
        log.debug("getTrialsByStatus() | status={}, limit={}", status, limit);

        List<ClinicalTrial> result = clinicalTrialPort.findByStatus(status, limit);

        log.debug("getTrialsByStatus() | return={} trials", result.size());
        return result;
    }

    @Override
    public List<ClinicalTrial> getTrialsByPhase(ClinicalTrialPhase phase, int limit) {
        log.debug("getTrialsByPhase() | phase={}, limit={}", phase, limit);

        List<ClinicalTrial> result = clinicalTrialPort.findByPhase(phase, limit);

        log.debug("getTrialsByPhase() | return={} trials", result.size());
        return result;
    }

    @Override
    public Optional<ClinicalTrial> getTrial(String trialId) {
        log.debug("getTrial() | trialId={}", trialId);

        Optional<ClinicalTrial> result = clinicalTrialPort.findById(trialId);

        log.debug("getTrial() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public int triggerHarvest() {
        log.debug("triggerHarvest()");

        List<ClinicalTrial> harvested = clinicalTrialHarvestingPort.harvestAll();
        log.info("triggerHarvest() | harvested {} raw trials", harvested.size());

        List<ClinicalTrial> newTrials = new ArrayList<>();
        for (ClinicalTrial trial : harvested) {
            if (!clinicalTrialPort.existsByNctId(trial.nctId())) {
                newTrials.add(trial);
            }
        }

        if (!newTrials.isEmpty()) {
            clinicalTrialPort.saveAll(newTrials);
        }

        log.info("triggerHarvest() | saved {} new trials (filtered {} duplicates)",
                 newTrials.size(), harvested.size() - newTrials.size());
        log.debug("triggerHarvest() | return={}", newTrials.size());
        return newTrials.size();
    }
}
