package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;

import java.util.List;

/**
 * Inbound port — compare AI vendors for a given healthcare query and return
 * a structured list of per-vendor assessments.
 *
 * <p>The method drives the COMBINED retrieval pipeline (Perplexity + DB sources)
 * and then invokes the vendor-specific synthesis prompt to produce one
 * {@link VendorAssessment} per vendor found in the retrieved sources.
 *
 * <p>Unlike {@link ConductResearchUseCase}, this port does not persist a
 * {@code ResearchRun} record — vendor comparisons are transient UI operations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-14
 * @updated 2026-05-14
 */
public interface CompareVendorsUseCase {

    /**
     * Run the vendor comparison pipeline for the given query.
     *
     * @param query      The healthcare AI research question; must not be blank.
     * @param maxSources Maximum number of source documents to retrieve; must be &ge; 1.
     * @return Ordered list of {@link VendorAssessment} records; empty if no vendors
     *         were identified in the retrieved sources.
     * @throws IllegalArgumentException if {@code query} is blank or {@code maxSources} &lt; 1.
     */
    List<VendorAssessment> compare(String query, int maxSources);
}
