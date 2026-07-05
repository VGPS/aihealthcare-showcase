package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record summarizing the outcome of a wiki lint run —
 * the automated quality-check pass that detects orphaned pages, broken
 * cross-references, stale content, and missing provenance.
 *
 * <p>Lint runs are persisted alongside {@link CompilationReport} records
 * so the wiki's health trend is observable over time.  Each issue category
 * lists the affected page slugs (or "source -> target" pairs for broken refs).
 *
 * @param runStartedAt       timestamp when the lint run began
 * @param runCompletedAt     timestamp when the lint run finished
 * @param totalPagesChecked  number of wiki pages examined
 * @param orphanedSlugs      pages with no incoming relatedSlugs references
 * @param brokenRefs         "source-slug -> target-slug" for non-existent targets
 * @param staleSlugs         pages not updated since the staleness threshold
 * @param missingProvenance  pages with zero SourceRef records
 * @param warnings           human-readable warnings emitted during the run
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
public record LintReport(
        Instant runStartedAt,
        Instant runCompletedAt,
        int totalPagesChecked,
        List<String> orphanedSlugs,
        List<String> brokenRefs,
        List<String> staleSlugs,
        List<String> missingProvenance,
        List<String> warnings
) {
    /**
     * Compact canonical constructor — validates required fields and
     * creates defensive copies of all list fields.
     */
    public LintReport {
        if (runStartedAt == null) {
            throw new IllegalArgumentException("runStartedAt must not be null");
        }
        if (runCompletedAt == null) {
            throw new IllegalArgumentException("runCompletedAt must not be null");
        }
        if (totalPagesChecked < 0) {
            throw new IllegalArgumentException(
                    "totalPagesChecked must be >= 0, got: " + totalPagesChecked);
        }
        if (orphanedSlugs == null) {
            throw new IllegalArgumentException("orphanedSlugs must not be null");
        }
        if (brokenRefs == null) {
            throw new IllegalArgumentException("brokenRefs must not be null");
        }
        if (staleSlugs == null) {
            throw new IllegalArgumentException("staleSlugs must not be null");
        }
        if (missingProvenance == null) {
            throw new IllegalArgumentException("missingProvenance must not be null");
        }
        if (warnings == null) {
            throw new IllegalArgumentException("warnings must not be null");
        }
        orphanedSlugs = List.copyOf(orphanedSlugs);
        brokenRefs = List.copyOf(brokenRefs);
        staleSlugs = List.copyOf(staleSlugs);
        missingProvenance = List.copyOf(missingProvenance);
        warnings = List.copyOf(warnings);
    }
}
