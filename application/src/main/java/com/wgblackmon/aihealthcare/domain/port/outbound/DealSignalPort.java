package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Outbound port for persisting and querying {@link DealSignal} records.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-04
 * @updated 2026-08-26
 */
public interface DealSignalPort {

    void saveAll(List<DealSignal> signals);

    List<DealSignal> findRecent(int pageSize, int page);

    boolean existsByArticleId(String articleId);

    DealSignal findById(String signalId);

    List<DealSignal> findByType(String signalType, int pageSize, int page);

    /** Returns signal count grouped by type for records whose detectedAt falls within [from, to]. */
    Map<String, Long> countByTypeInPeriod(Instant from, Instant to);
}
