package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link ClinicalTrial} records.
 *
 * <p>Implementations live in the infrastructure layer (e.g. JPA adapter).
 * Provides a deduplication check by NCT ID to prevent storing duplicate
 * trials across harvest runs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
public interface ClinicalTrialPort {

    /**
     * Saves (inserts or updates) a clinical trial.
     */
    void save(ClinicalTrial trial);

    /**
     * Saves multiple clinical trials in batch.
     */
    void saveAll(List<ClinicalTrial> trials);

    /**
     * Checks if a clinical trial with the given NCT ID already exists.
     * Used for deduplication across harvest runs.
     */
    boolean existsByNctId(String nctId);

    /**
     * Returns the most recent clinical trials, ordered by discoveredAt descending.
     */
    List<ClinicalTrial> findRecent(int limit);

    /**
     * Returns recent trials filtered by recruitment status.
     */
    List<ClinicalTrial> findByStatus(ClinicalTrialStatus status, int limit);

    /**
     * Returns recent trials filtered by phase.
     */
    List<ClinicalTrial> findByPhase(ClinicalTrialPhase phase, int limit);

    /**
     * Finds a single clinical trial by its internal ID.
     */
    Optional<ClinicalTrial> findById(String trialId);

    /**
     * Searches trials whose title, summary, sponsor, or conditions
     * contain the given keyword (case-insensitive).
     */
    List<ClinicalTrial> findByKeyword(String keyword, int limit);
}
