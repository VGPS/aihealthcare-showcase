package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Inbound port for detecting deal signals in recently harvested articles
 * and retrieving enriched deal context with cross-referenced data.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-04
 * @updated 2026-08-26
 */
public interface DetectDealSignalsUseCase {

    List<DealSignal> detectSignals();

    List<DealSignal> getRecentSignals(int pageSize, int page);

    DealSignal getSignalById(String signalId);

    List<DealSignal> getSignalsByType(DealSignalType type, int pageSize, int page);

    DealContext getSignalWithContext(String signalId);

    /** Returns signal count grouped by type for the given time window. */
    Map<String, Long> getTypeStats(Instant from, Instant to);
}
