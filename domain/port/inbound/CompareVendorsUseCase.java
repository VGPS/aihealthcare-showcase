package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.VendorCompareResult;

import java.util.List;

/**
 * Inbound port — compare AI vendors for a given healthcare query and return
 * a structured result containing per-vendor assessments and the source citations
 * used during evaluation.
 *
 * <p>Two modes are supported:
 * <ul>
 *   <li><b>Free-form query</b> — the COMBINED retrieval pipeline discovers vendors
 *       from the retrieved sources (original behavior).</li>
 *   <li><b>Vendor-select</b> — the caller provides explicit vendor topic names and
 *       articles are fetched directly per vendor, bypassing query decomposition.</li>
 * </ul>
 *
 * <p>Unlike {@link ConductResearchUseCase}, this port does not persist a
 * {@code ResearchRun} record — vendor comparisons are transient UI operations.
 *
 * @author  Bill Blackmon
 * @version 1.4
 * @since   2026-05-14
 * @updated 2026-07-07
 */
public interface CompareVendorsUseCase {

    /**
     * Run the vendor comparison pipeline for a free-form query.
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

    /**
     * Run the vendor comparison pipeline for explicitly selected vendor topics.
     *
     * <p>Articles are fetched directly per vendor topic name (e.g. "Anthropic Healthcare"),
     * bypassing query decomposition and relevance filtering.  An optional {@code focusArea}
     * narrows the AI comparison to a specific healthcare domain.
     *
     * @param vendorTopics Non-empty list of vendor topic names matching feed config topics.
     * @param focusArea    Optional healthcare focus area (e.g. "radiology"); may be null or blank.
     * @param maxSources   Maximum source documents per vendor topic; must be &ge; 1.
     * @param scoring      Scoring algorithm: {@code "TF_IDF"} or {@code "DOC_FREQUENCY"}.
     * @return A {@link VendorCompareResult} containing vendors sorted by relevance descending.
     * @throws IllegalArgumentException if {@code vendorTopics} is empty or {@code maxSources} &lt; 1.
     */
    VendorCompareResult compareSelected(List<String> vendorTopics, String focusArea,
                                         int maxSources, String scoring);
}
