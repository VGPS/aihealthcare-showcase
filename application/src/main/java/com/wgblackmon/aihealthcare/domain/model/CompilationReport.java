package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record summarizing the outcome of a single wiki
 * compilation run — the process by which newly harvested articles are
 * integrated into the LLM-maintained knowledge base.
 *
 * <p>Compilation is observable: every run produces a {@code CompilationReport}
 * suitable for demos, logging, and audit trails.  The report captures which
 * pages were created or updated, any contradictions flagged, and any
 * warnings emitted during the run.
 *
 * @param runStartedAt          timestamp when compilation began
 * @param runCompletedAt        timestamp when compilation finished
 * @param articlesProcessed     number of source articles consumed in this run
 * @param pagesCreated          slugs of wiki pages created during this run
 * @param pagesUpdated          slugs of wiki pages revised during this run
 * @param contradictionsFlagged contradictions detected during compilation
 * @param warnings              human-readable warnings (e.g. parse failures,
 *                              skipped articles)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public record CompilationReport(
        Instant runStartedAt,
        Instant runCompletedAt,
        int articlesProcessed,
        List<String> pagesCreated,
        List<String> pagesUpdated,
        List<Contradiction> contradictionsFlagged,
        List<String> warnings
) {
    /**
     * Compact canonical constructor — validates required fields and
     * creates defensive copies of all list fields.
     */
    public CompilationReport {
        if (runStartedAt == null) {
            throw new IllegalArgumentException("runStartedAt must not be null");
        }
        if (runCompletedAt == null) {
            throw new IllegalArgumentException("runCompletedAt must not be null");
        }
        if (articlesProcessed < 0) {
            throw new IllegalArgumentException(
                    "articlesProcessed must be >= 0, got: " + articlesProcessed);
        }
        if (pagesCreated == null) {
            throw new IllegalArgumentException("pagesCreated must not be null");
        }
        if (pagesUpdated == null) {
            throw new IllegalArgumentException("pagesUpdated must not be null");
        }
        if (contradictionsFlagged == null) {
            throw new IllegalArgumentException("contradictionsFlagged must not be null");
        }
        if (warnings == null) {
            throw new IllegalArgumentException("warnings must not be null");
        }
        pagesCreated = List.copyOf(pagesCreated);
        pagesUpdated = List.copyOf(pagesUpdated);
        contradictionsFlagged = List.copyOf(contradictionsFlagged);
        warnings = List.copyOf(warnings);
    }
}
