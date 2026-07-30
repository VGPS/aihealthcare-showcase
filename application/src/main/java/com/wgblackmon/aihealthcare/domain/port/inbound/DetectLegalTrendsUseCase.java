package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;

import java.util.Optional;

/**
 * Inbound port for the legal trend detection use case.
 *
 * <p>Provides operations to detect trends in legal, regulatory, and policy
 * articles and to retrieve the latest snapshot for display.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
public interface DetectLegalTrendsUseCase {

    /**
     * Runs trend detection on legal/regulatory/policy articles and persists
     * the resulting snapshot.
     *
     * @return the computed snapshot
     */
    LegalTrendSnapshot detectLegalTrends();

    /**
     * Returns the most recently computed legal trend snapshot.
     *
     * @return the latest snapshot, or empty if none exists
     */
    Optional<LegalTrendSnapshot> getLatestSnapshot();
}
