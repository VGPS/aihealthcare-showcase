package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for LLM-powered healthcare framework analysis.
 *
 * <p>Implementations send articles about a company to an LLM and parse
 * the structured response into a {@link FrameworkAnalysis} with scored
 * dimensions, strengths, weaknesses, and overall assessment.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface FrameworkLlmPort {

    /**
     * Analyzes a company's healthcare AI framework from its article corpus.
     *
     * @param companySlug the company slug
     * @param companyName the company display name
     * @param articles    articles about this company's healthcare framework
     * @return structured analysis, or null if the LLM could not produce one
     */
    FrameworkAnalysis analyze(String companySlug, String companyName, List<NewsArticle> articles);
}
