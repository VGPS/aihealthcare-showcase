package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.LintReport;

import java.util.List;

/**
 * Outbound port for persisting and retrieving wiki lint reports.
 *
 * <p>Each run of the wiki linter produces a {@link LintReport} that is
 * persisted through this port for dashboard display, trend analysis,
 * and audit trail purposes.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
public interface LintReportPort {

    /**
     * Persists a lint report.
     *
     * @param report the report to save; must not be {@code null}
     */
    void save(LintReport report);

    /**
     * Retrieves all lint reports, ordered by run start time
     * descending (most recent first).
     *
     * @return all reports, newest first; empty list if none
     */
    List<LintReport> findAll();

    /**
     * Retrieves the most recent lint report.
     *
     * @return the latest report, or {@code null} if no reports exist
     */
    LintReport findLatest();
}
