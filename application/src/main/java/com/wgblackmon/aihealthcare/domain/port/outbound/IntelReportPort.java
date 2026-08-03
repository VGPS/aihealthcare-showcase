package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.IntelReport;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying competitive intelligence reports.
 *
 * <p>Implementations live in {@code infrastructure.persistence}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface IntelReportPort {

    /**
     * Persists a report.
     *
     * @param report the report to save
     */
    void save(IntelReport report);

    /**
     * Retrieves a report by its ID.
     *
     * @param reportId the report UUID
     * @return the report if found
     */
    Optional<IntelReport> findById(String reportId);

    /**
     * Retrieves all reports, most recent first.
     *
     * @return list of reports ordered by generatedAt descending
     */
    List<IntelReport> findAll();
}
