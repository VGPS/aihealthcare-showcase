package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure domain service that performs automated quality checks on the
 * wiki knowledge base.
 *
 * <p>The linter detects four categories of wiki health issues:
 * <ul>
 *   <li><b>Orphaned pages</b> — pages with no incoming {@code relatedSlugs}
 *       references from any other page</li>
 *   <li><b>Broken cross-references</b> — {@code relatedSlugs} entries pointing
 *       to slugs that do not exist in the wiki</li>
 *   <li><b>Stale pages</b> — pages not updated within the configured
 *       staleness threshold (days)</li>
 *   <li><b>Missing provenance</b> — pages with zero {@code SourceRef} records,
 *       violating the wiki's provenance-first principle</li>
 * </ul>
 *
 * <p>This service is framework-free (no Spring dependencies) and operates
 * on a snapshot of all wiki pages passed in by the caller.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
public class WikiLintService {

    /**
     * Runs the full lint pass on the given wiki pages.
     *
     * @param pages                   all wiki pages to check; must not be {@code null}
     * @param stalenessThresholdDays  pages not updated in this many days are flagged as stale
     * @return a {@link LintReport} summarizing all issues found
     */
    public LintReport lint(List<WikiPage> pages, int stalenessThresholdDays) {
        Instant startedAt = Instant.now();

        // Build lookup sets
        Set<String> allSlugs = new HashSet<>();
        for (WikiPage page : pages) {
            allSlugs.add(page.slug());
        }

        // Build set of slugs that are referenced by at least one other page
        Set<String> referencedSlugs = new HashSet<>();
        for (WikiPage page : pages) {
            for (String relatedSlug : page.relatedSlugs()) {
                referencedSlugs.add(relatedSlug);
            }
        }

        List<String> orphanedSlugs = new ArrayList<>();
        List<String> brokenRefs = new ArrayList<>();
        List<String> staleSlugs = new ArrayList<>();
        List<String> missingProvenance = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Instant stalenessThreshold = Instant.now().minus(stalenessThresholdDays, ChronoUnit.DAYS);

        for (WikiPage page : pages) {
            // Orphan detection: slug not referenced by any other page
            if (!referencedSlugs.contains(page.slug())) {
                orphanedSlugs.add(page.slug());
            }

            // Broken cross-references
            for (String relatedSlug : page.relatedSlugs()) {
                if (!allSlugs.contains(relatedSlug)) {
                    brokenRefs.add(page.slug() + " -> " + relatedSlug);
                }
            }

            // Stale detection
            Instant lastTouch = page.updatedAt() != null ? page.updatedAt() : page.createdAt();
            if (lastTouch.isBefore(stalenessThreshold)) {
                staleSlugs.add(page.slug());
            }

            // Missing provenance
            if (page.sources().isEmpty()) {
                missingProvenance.add(page.slug());
            }
        }

        Instant completedAt = Instant.now();

        return new LintReport(
                startedAt,
                completedAt,
                pages.size(),
                orphanedSlugs,
                brokenRefs,
                staleSlugs,
                missingProvenance,
                warnings
        );
    }
}
