package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record bundling the output of a vendor comparison pipeline run:
 * a list of assessed vendors and the deduplicated source citations that were evaluated.
 *
 * <p>This record allows the UI layer to display both the vendor assessment cards and
 * a numbered reference table of the source documents used by the AI.
 *
 * @param vendors   List of {@link VendorAssessment} records sorted by relevance descending.
 * @param citations Deduplicated, numbered source citations assembled by {@code CitationAssembler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-08
 * @updated 2026-06-08
 */
public record VendorCompareResult(
        List<VendorAssessment> vendors,
        List<SourceCitation> citations
) {}
