package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing a single query sent to a
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort} adapter.
 *
 * <p>Each {@link ResearchPlan#subQueries()} entry is wrapped in a {@code RetrievalQuery}
 * before being dispatched to the retrieval adapter.  The {@code engine} field tells the
 * adapter which search backend to target; the adapter may ignore it when only one backend
 * is configured.
 *
 * @param text        The search text to submit to the retrieval backend; must not be blank.
 * @param engine      Logical backend name (e.g., {@code "GOOGLE"}, {@code "PERPLEXITY"}).
 * @param maxResults  Maximum number of sources to return; must be ≥ 1.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record RetrievalQuery(
        String text,
        String engine,
        int maxResults
) {}
