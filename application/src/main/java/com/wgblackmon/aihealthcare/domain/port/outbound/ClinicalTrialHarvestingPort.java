package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;

import java.util.List;

/**
 * Outbound port for harvesting clinical trials from external sources
 * (e.g. ClinicalTrials.gov).
 *
 * <p>Implementations live in the infrastructure layer and handle API
 * calls, response parsing, and AI-relevance filtering. The returned
 * trials may include duplicates — deduplication is the caller's
 * responsibility.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
public interface ClinicalTrialHarvestingPort {

    /**
     * Harvests clinical trials from all configured sources.
     *
     * @return list of discovered trials (may include duplicates)
     */
    List<ClinicalTrial> harvestAll();
}
