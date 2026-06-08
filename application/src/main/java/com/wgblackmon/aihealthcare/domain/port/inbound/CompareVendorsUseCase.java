package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.VendorCompareResult;

/**
 * Inbound port — compare AI vendors for a given healthcare query and return
 * a structured result containing per-vendor assessments and the source citations
 * used during evaluation.
 *
 * <p>The method drives the COMBINED retrieval pipeline (Perplexity + DB sources)
 * and then invokes the vendor-specific synthesis prompt to produce one
 * {@link com.wgblackmon.aihealthcare.domain.model.VendorAssessment} per vendor
 * found in the retrieved sources.
 *
 * <p>Unlike {@link ConductResearchUseCase}, this port does not persist a
 * {@code ResearchRun} record — vendor comparisons are transient UI operations.
 *
 * @author  Bill Blackmon
 * @version 1.3
 * @since   2026-05-14
 * @updated 2026-06-08
 */
public interface CompareVendorsUseCase {

    /**
     * Run the vendor comparison pipeline for the given query.
     *
     * @param query      The healthcare AI research question; must not be blank.
     * @param maxSources Maximum number of source documents to retrieve; must be &ge; 1.
     * @param minVendors Minimum number of vendor sections to request from the AI; must be &ge; 1.
     * @param scoring    Scoring algorithm: {@code "TF_IDF"} or {@code "DOC_FREQUENCY"} (default).
     * @return A {@link VendorCompareResult} containing vendors sorted by relevance descending
     *         and the deduplicated source citations evaluated.
     * @throws IllegalArgumentException if {@code query} is blank or {@code maxSources} &lt; 1.
     */
    VendorCompareResult compare(String query, int maxSources, int minVendors, String scoring);
}
