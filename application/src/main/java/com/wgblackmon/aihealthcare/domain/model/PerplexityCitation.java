package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a citation logged from a Perplexity API
 * call during the company discovery pipeline.
 *
 * <p>Every Perplexity API call (discovery, extraction, validation) produces
 * citations — URLs cited by the model as sources for its claims. This record
 * captures each citation for audit traceability of factual claims.
 *
 * @param citationId   UUID primary key
 * @param companyId    FK to {@link HealthcareAiCompany} (nullable for discovery calls)
 * @param url          the cited source URL
 * @param context      snippet or context around the citation
 * @param callType     which pipeline phase produced this citation
 * @param retrievedAt  when the citation was retrieved from Perplexity
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public record PerplexityCitation(
        String citationId,
        String companyId,
        String url,
        String context,
        CompanyCallType callType,
        Instant retrievedAt
) {

    /**
     * Compact constructor — validates required fields.
     */
    public PerplexityCitation {
        if (citationId == null || citationId.isBlank()) {
            throw new IllegalArgumentException("citationId must not be blank");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (callType == null) {
            throw new IllegalArgumentException("callType must not be null");
        }
        if (retrievedAt == null) {
            throw new IllegalArgumentException("retrievedAt must not be null");
        }
    }
}
