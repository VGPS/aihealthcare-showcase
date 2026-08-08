package com.wgblackmon.aihealthcare.domain.port.outbound;

import java.util.List;
import java.util.Map;

/**
 * Outbound port for AI-powered company research via the Perplexity API.
 *
 * <p>Encapsulates three distinct API call types used in the company discovery
 * pipeline:
 * <ol>
 *   <li><strong>Discovery</strong> — broad "wide research" to find new company names</li>
 *   <li><strong>Extraction</strong> — structured field extraction for a single company</li>
 *   <li><strong>Validation</strong> — cross-check against curated industry lists</li>
 * </ol>
 *
 * <p>Implementations live in the infrastructure layer (Perplexity REST adapter).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public interface CompanyResearchPort {

    /**
     * Result from a broad discovery call — company names + citation URLs.
     *
     * @param companyNames  discovered company names
     * @param citations     source URLs cited by the model
     * @param rawContent    full response content for context extraction
     */
    record DiscoveryResult(List<String> companyNames, List<String> citations, String rawContent) {}

    /**
     * Result from a structured extraction call — field map + citation URLs.
     *
     * @param fields     extracted fields as key-value pairs matching entity schema
     * @param citations  source URLs cited during extraction
     */
    record ExtractionResult(Map<String, Object> fields, List<String> citations) {}

    /**
     * Result from a cross-validation call — validation status + source URLs.
     *
     * @param validated         whether the company appears on curated lists
     * @param validationSources URLs from validation sources
     * @param details           validation details text
     */
    record ValidationResult(boolean validated, List<String> validationSources, String details) {}

    /**
     * Runs a broad discovery query to find AI healthcare companies.
     *
     * @param prompt the discovery prompt
     * @return discovery result with company names and citations; never null
     */
    DiscoveryResult discoverCompanies(String prompt);

    /**
     * Extracts structured fields for a single company using JSON schema output.
     *
     * @param companyName the company name to research
     * @return extraction result with field map and citations; never null
     */
    ExtractionResult extractCompanyFields(String companyName);

    /**
     * Cross-validates a company against free curated industry lists.
     *
     * @param companyName the company name to validate
     * @return validation result with status and source URLs; never null
     */
    ValidationResult crossValidate(String companyName);

    /**
     * Returns true if the adapter is configured and ready (API key present).
     */
    boolean isAvailable();
}
