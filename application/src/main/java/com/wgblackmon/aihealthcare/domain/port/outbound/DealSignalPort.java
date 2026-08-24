package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;

import java.util.List;

/**
 * Outbound port for persisting and querying {@link DealSignal} records.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-04
 * @updated 2026-08-23
 */
public interface DealSignalPort {

    void saveAll(List<DealSignal> signals);

    List<DealSignal> findRecent(int pageSize, int page);

    boolean existsByArticleId(String articleId);

    DealSignal findById(String signalId);

    List<DealSignal> findByType(String signalType, int pageSize, int page);
}
