package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;

import java.util.List;

/**
 * Outbound port for persisting and querying {@link DealSignal} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-06
 */
public interface DealSignalPort {

    void saveAll(List<DealSignal> signals);

    List<DealSignal> findRecent(int limit);

    boolean existsByArticleId(String articleId);

    DealSignal findById(String signalId);

    List<DealSignal> findByType(String signalType, int limit);
}
