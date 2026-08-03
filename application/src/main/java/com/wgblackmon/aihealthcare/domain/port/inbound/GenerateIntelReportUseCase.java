package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.IntelReport;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for generating and retrieving competitive intelligence reports.
 *
 * <p>A report is an on-demand, AI-generated deep-dive analysis of a company
 * or market segment, built from sources gathered by the COMBINED research
 * pipeline. Reports are persisted for future retrieval.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface GenerateIntelReportUseCase {

    /**
     * Generates a new competitive intelligence report for the given query.
     *
     * @param query     free-form company name or market segment
     * @param userEmail email of the requesting user
     * @return the generated and persisted report
     */
    IntelReport generate(String query, String userEmail);

    /**
     * Retrieves a previously generated report by its ID.
     *
     * @param reportId the report UUID
     * @return the report if found
     */
    Optional<IntelReport> findById(String reportId);

    /**
     * Retrieves all generated reports, most recent first.
     *
     * @return list of reports ordered by generatedAt descending
     */
    List<IntelReport> findAll();
}
