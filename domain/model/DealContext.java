package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Enrichment wrapper that combines a {@link DealSignal} with cross-referenced
 * context from other analytical pipelines: sentiment, framework analysis,
 * regulatory events, and company profile.
 *
 * <p>All cross-reference fields are nullable — the enrichment degrades
 * gracefully when data is unavailable for a given company.
 *
 * @param signal            the core deal signal (never null)
 * @param sentiment         company sentiment if matched, nullable
 * @param framework         framework competitive analysis if matched, nullable
 * @param regulatoryEvents  related regulatory events, empty list if none
 * @param companyProfile    company profile if matched, nullable
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
public record DealContext(
        DealSignal signal,
        CompanySentiment sentiment,
        FrameworkAnalysis framework,
        List<RegulatoryEvent> regulatoryEvents,
        CompanyProfile companyProfile
) {

    public DealContext {
        if (signal == null) {
            throw new IllegalArgumentException("signal must not be null");
        }
        if (regulatoryEvents == null) {
            regulatoryEvents = List.of();
        }
    }
}
