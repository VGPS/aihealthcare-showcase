package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for healthcare framework competitive analysis.
 *
 * <p>Drives deep LLM analysis of configured companies' healthcare AI
 * frameworks, scoring across multiple dimensions and producing
 * analyst-grade assessments.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface AnalyzeFrameworksUseCase {

    /**
     * Runs competitive analysis on all configured framework companies.
     *
     * @return list of completed analyses
     */
    List<FrameworkAnalysis> analyzeAll();

    /**
     * Returns the most recent analysis for the given company.
     *
     * @param companySlug the company's slug identifier
     * @return the analysis, or empty if never analyzed
     */
    Optional<FrameworkAnalysis> getBySlug(String companySlug);

    /**
     * Returns all stored analyses, ordered by overall score descending.
     *
     * @return all analyses; may be empty
     */
    List<FrameworkAnalysis> getAll();
}
