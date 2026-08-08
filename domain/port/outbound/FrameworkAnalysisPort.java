package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link FrameworkAnalysis} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface FrameworkAnalysisPort {

    void save(FrameworkAnalysis analysis);

    Optional<FrameworkAnalysis> findBySlug(String companySlug);

    List<FrameworkAnalysis> findAll();
}
