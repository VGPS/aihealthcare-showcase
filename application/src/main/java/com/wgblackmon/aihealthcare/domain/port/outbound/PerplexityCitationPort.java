package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.PerplexityCitation;

import java.util.List;

/**
 * Outbound port for persisting and querying {@link PerplexityCitation} audit
 * trail records from the company discovery pipeline.
 *
 * <p>Every Perplexity API call logs its citations here for traceability
 * of factual claims about companies.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public interface PerplexityCitationPort {

    /**
     * Saves a single citation record.
     */
    void save(PerplexityCitation citation);

    /**
     * Saves multiple citation records.
     */
    void saveAll(List<PerplexityCitation> citations);

    /**
     * Finds all citations linked to a specific company.
     */
    List<PerplexityCitation> findByCompanyId(String companyId);
}
