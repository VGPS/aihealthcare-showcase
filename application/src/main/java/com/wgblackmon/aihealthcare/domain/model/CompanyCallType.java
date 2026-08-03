package com.wgblackmon.aihealthcare.domain.model;

/**
 * Enum representing the type of Perplexity API call that produced a citation.
 *
 * <p>Used to categorize entries in the {@code perplexity_citations} audit trail
 * table, distinguishing between the three phases of the company discovery
 * pipeline: broad discovery, structured field extraction, and cross-validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public enum CompanyCallType {

    /** Broad "wide research" discovery call to find new company names. */
    DISCOVERY,

    /** Structured extraction call to pull clean fields for a single company. */
    EXTRACTION,

    /** Cross-validation call checking company against curated lists. */
    VALIDATION
}
