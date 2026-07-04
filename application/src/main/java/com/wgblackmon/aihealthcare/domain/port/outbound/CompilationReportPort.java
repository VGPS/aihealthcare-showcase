package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;

import java.util.List;

/**
 * Outbound port for persisting and retrieving wiki compilation reports.
 *
 * <p>Each run of the {@link KnowledgeCompilationPort} produces a
 * {@link CompilationReport} that is persisted through this port for
 * dashboard display and audit trail purposes.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public interface CompilationReportPort {

    /**
     * Persists a compilation report.
     *
     * @param report the report to save; must not be {@code null}
     */
    void save(CompilationReport report);

    /**
     * Retrieves all compilation reports, ordered by run start time
     * descending (most recent first).
     *
     * @return all reports, newest first; empty list if none
     */
    List<CompilationReport> findAll();
}
