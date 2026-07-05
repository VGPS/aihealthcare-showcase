package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WikiLintService}.
 *
 * <p>Verifies detection of orphaned pages, broken cross-references,
 * stale content, and missing provenance across various wiki states.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
class WikiLintServiceTest {

    private WikiLintService lintService;

    private static final Instant NOW = Instant.now();
    private static final Instant RECENT = NOW.minus(5, ChronoUnit.DAYS);
    private static final Instant OLD = NOW.minus(60, ChronoUnit.DAYS);
    private static final SourceRef SAMPLE_SOURCE = new SourceRef("art-1", "PubMed", LocalDate.of(2026, 7, 1), "excerpt");

    @BeforeEach
    void setUp() {
        lintService = new WikiLintService();
    }

    @Test
    void lint_emptyWiki_returnsCleanReport() {
        LintReport report = lintService.lint(List.of(), 30);

        assertThat(report.totalPagesChecked()).isZero();
        assertThat(report.orphanedSlugs()).isEmpty();
        assertThat(report.brokenRefs()).isEmpty();
        assertThat(report.staleSlugs()).isEmpty();
        assertThat(report.missingProvenance()).isEmpty();
    }

    @Test
    void lint_detectsOrphanedPages() {
        // page-a references page-b, but nobody references page-a or page-c
        WikiPage pageA = makePage("page-a", RECENT, List.of(SAMPLE_SOURCE), List.of("page-b"));
        WikiPage pageB = makePage("page-b", RECENT, List.of(SAMPLE_SOURCE), List.of());
        WikiPage pageC = makePage("page-c", RECENT, List.of(SAMPLE_SOURCE), List.of());

        LintReport report = lintService.lint(List.of(pageA, pageB, pageC), 30);

        // page-b is referenced by page-a, so only page-a and page-c are orphans
        assertThat(report.orphanedSlugs()).containsExactlyInAnyOrder("page-a", "page-c");
    }

    @Test
    void lint_detectsBrokenCrossReferences() {
        WikiPage pageA = makePage("page-a", RECENT, List.of(SAMPLE_SOURCE), List.of("page-b", "non-existent"));
        WikiPage pageB = makePage("page-b", RECENT, List.of(SAMPLE_SOURCE), List.of("also-missing"));

        LintReport report = lintService.lint(List.of(pageA, pageB), 30);

        assertThat(report.brokenRefs()).containsExactlyInAnyOrder(
                "page-a -> non-existent",
                "page-b -> also-missing"
        );
    }

    @Test
    void lint_detectsStalePages() {
        WikiPage fresh = makePage("fresh-page", RECENT, List.of(SAMPLE_SOURCE), List.of());
        WikiPage stale = makePage("stale-page", OLD, List.of(SAMPLE_SOURCE), List.of());

        LintReport report = lintService.lint(List.of(fresh, stale), 30);

        assertThat(report.staleSlugs()).containsExactly("stale-page");
    }

    @Test
    void lint_detectsMissingProvenance() {
        WikiPage withSources = makePage("has-sources", RECENT, List.of(SAMPLE_SOURCE), List.of());
        WikiPage noSources = makePage("no-sources", RECENT, List.of(), List.of());

        LintReport report = lintService.lint(List.of(withSources, noSources), 30);

        assertThat(report.missingProvenance()).containsExactly("no-sources");
    }

    @Test
    void lint_cleanWiki_noIssues() {
        // Two pages referencing each other, both have sources, both recent
        WikiPage pageA = makePage("page-a", RECENT, List.of(SAMPLE_SOURCE), List.of("page-b"));
        WikiPage pageB = makePage("page-b", RECENT, List.of(SAMPLE_SOURCE), List.of("page-a"));

        LintReport report = lintService.lint(List.of(pageA, pageB), 30);

        assertThat(report.totalPagesChecked()).isEqualTo(2);
        assertThat(report.orphanedSlugs()).isEmpty();
        assertThat(report.brokenRefs()).isEmpty();
        assertThat(report.staleSlugs()).isEmpty();
        assertThat(report.missingProvenance()).isEmpty();
    }

    @Test
    void lint_mixedIssues_detectsAll() {
        WikiPage orphanStale = makePage("orphan-stale", OLD, List.of(SAMPLE_SOURCE), List.of("ghost"));
        WikiPage referenced = makePage("referenced", RECENT, List.of(), List.of("orphan-stale"));

        LintReport report = lintService.lint(List.of(orphanStale, referenced), 30);

        // orphan-stale is referenced by 'referenced', so it's NOT orphaned
        // 'referenced' is not referenced by anyone, so it IS orphaned
        assertThat(report.orphanedSlugs()).containsExactly("referenced");
        assertThat(report.brokenRefs()).containsExactly("orphan-stale -> ghost");
        assertThat(report.staleSlugs()).containsExactly("orphan-stale");
        assertThat(report.missingProvenance()).containsExactly("referenced");
    }

    @Test
    void lint_usesUpdatedAtOverCreatedAtForStaleness() {
        // Page created long ago but updated recently — should NOT be stale
        WikiPage updatedRecently = new WikiPage(
                "updated-recently", "Updated Recently", WikiPageType.ENTITY,
                List.of("healthcare"), "Content",
                List.of(SAMPLE_SOURCE), List.of(),
                OLD, RECENT, 2
        );

        LintReport report = lintService.lint(List.of(updatedRecently), 30);

        assertThat(report.staleSlugs()).isEmpty();
    }

    private WikiPage makePage(String slug, Instant createdAt,
                              List<SourceRef> sources, List<String> relatedSlugs) {
        return new WikiPage(
                slug, slug + " Title", WikiPageType.ENTITY,
                List.of("healthcare"), "Content for " + slug,
                sources, relatedSlugs,
                createdAt, null, 1
        );
    }
}
