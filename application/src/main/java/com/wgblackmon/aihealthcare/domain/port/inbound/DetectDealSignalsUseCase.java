package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;

import java.util.List;

/**
 * Inbound port for detecting deal signals in recently harvested articles.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface DetectDealSignalsUseCase {

    List<DealSignal> detectSignals();

    List<DealSignal> getRecentSignals(int limit);
}
