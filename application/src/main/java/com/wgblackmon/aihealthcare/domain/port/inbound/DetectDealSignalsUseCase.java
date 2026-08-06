package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;

import java.util.List;

/**
 * Inbound port for detecting deal signals in recently harvested articles
 * and retrieving enriched deal context with cross-referenced data.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-06
 */
public interface DetectDealSignalsUseCase {

    List<DealSignal> detectSignals();

    List<DealSignal> getRecentSignals(int limit);

    DealSignal getSignalById(String signalId);

    List<DealSignal> getSignalsByType(DealSignalType type, int limit);

    DealContext getSignalWithContext(String signalId);
}
