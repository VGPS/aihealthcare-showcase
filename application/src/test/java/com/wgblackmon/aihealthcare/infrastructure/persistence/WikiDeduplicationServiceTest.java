package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WikiDeduplicationService}.
 *
 * <p>Verifies grouping logic, canonical selection, and cascade delete order
 * without hitting the database.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-07
 * @updated 2026-10-07
 */
class WikiDeduplicationServiceTest {

    private WikiPageRepository pageRepository;
    private WikiSourceRefRepository sourceRefRepository;
    private WikiContradictionRepository contradictionRepository;
    private WikiPageRevisionRepository revisionRepository;
    private WikiDeduplicationService service;

    @BeforeEach
    void setUp() {
        pageRepository = mock(WikiPageRepository.class);
        sourceRefRepository = mock(WikiSourceRefRepository.class);
        contradictionRepository = mock(WikiContradictionRepository.class);
        revisionRepository = mock(WikiPageRevisionRepository.class);
        service = new WikiDeduplicationService(
                pageRepository, sourceRefRepository, contradictionRepository, revisionRepository);
    }

    @Test
    void deduplicate_noPages_returnsZeroCounts() {
        when(pageRepository.findAll()).thenReturn(List.of());

        WikiDeduplicationService.DeduplicationResult result = service.deduplicate();

        assertThat(result.groupsDeduplicated()).isZero();
        assertThat(result.pagesDeleted()).isZero();
        assertThat(result.remainingPages()).isZero();
        verifyNoInteractions(sourceRefRepository, contradictionRepository, revisionRepository);
    }

    @Test
    void deduplicate_noDuplicateTitles_deletesNothing() {
        when(pageRepository.findAll()).thenReturn(List.of(
                page("akasa", "AKASA AI Coding", 2),
                page("aidoc", "Aidoc AI Platform", 3)
        ));

        WikiDeduplicationService.DeduplicationResult result = service.deduplicate();

        assertThat(result.pagesDeleted()).isZero();
        assertThat(result.groupsDeduplicated()).isZero();
        verifyNoInteractions(sourceRefRepository);
    }

    @Test
    void deduplicate_oneDuplicate_deletesLowerRevision() {
        WikiPageEntity canonical = page("akasa", "AKASA AI Coding", 3);
        WikiPageEntity duplicate = page("akasa-2026", "AKASA AI Coding", 1);
        when(pageRepository.findAll()).thenReturn(List.of(canonical, duplicate));

        WikiDeduplicationService.DeduplicationResult result = service.deduplicate();

        assertThat(result.pagesDeleted()).isEqualTo(1);
        assertThat(result.groupsDeduplicated()).isEqualTo(1);
        assertThat(result.remainingPages()).isEqualTo(1);

        verify(sourceRefRepository).deleteByPageSlug("akasa-2026");
        verify(contradictionRepository).deleteByPageSlug("akasa-2026");
        verify(revisionRepository).deleteByPageSlug("akasa-2026");
        verify(pageRepository).delete(duplicate);

        // canonical must not be deleted
        verify(sourceRefRepository, never()).deleteByPageSlug("akasa");
        verify(pageRepository, never()).delete(canonical);
    }

    @Test
    void deduplicate_sameRevision_prefersShortestSlug() {
        WikiPageEntity shorter = page("akasa", "AKASA AI Coding", 2);
        WikiPageEntity longer = page("akasa-update-2026", "AKASA AI Coding", 2);
        when(pageRepository.findAll()).thenReturn(List.of(longer, shorter));

        service.deduplicate();

        // longer slug is the non-canonical one
        verify(pageRepository).delete(longer);
        verify(sourceRefRepository, never()).deleteByPageSlug("akasa");
    }

    @Test
    void deduplicate_tripleGroup_deletesTwo() {
        WikiPageEntity v1 = page("fda-ai", "FDA AI Framework", 1);
        WikiPageEntity v2 = page("fda-ai-2026", "FDA AI Framework", 2);
        WikiPageEntity v3 = page("fda-ai-update", "FDA AI Framework", 1);
        when(pageRepository.findAll()).thenReturn(List.of(v1, v2, v3));

        WikiDeduplicationService.DeduplicationResult result = service.deduplicate();

        assertThat(result.pagesDeleted()).isEqualTo(2);
        assertThat(result.groupsDeduplicated()).isEqualTo(1);

        // v2 has highest revision — it is canonical
        verify(pageRepository, never()).delete(v2);
        verify(pageRepository).delete(v1);
        verify(pageRepository).delete(v3);
    }

    @Test
    void deduplicate_multipleIndependentGroups_processesAll() {
        WikiPageEntity akasa1 = page("akasa", "AKASA AI Coding", 2);
        WikiPageEntity akasa2 = page("akasa-2026", "AKASA AI Coding", 1);
        WikiPageEntity fda1   = page("fda-ai", "FDA AI Framework", 3);
        WikiPageEntity fda2   = page("fda-ai-2026", "FDA AI Framework", 1);
        when(pageRepository.findAll()).thenReturn(List.of(akasa1, akasa2, fda1, fda2));

        WikiDeduplicationService.DeduplicationResult result = service.deduplicate();

        assertThat(result.pagesDeleted()).isEqualTo(2);
        assertThat(result.groupsDeduplicated()).isEqualTo(2);
        assertThat(result.remainingPages()).isEqualTo(2);
    }

    // ── normalizeTitle ────────────────────────────────────────────────────────

    @Test
    void normalizeTitle_stripsSpecialCharsAndLowercases() {
        assertThat(WikiDeduplicationService.normalizeTitle("AKASA — Autonomous AI (2026 Update)"))
                .isEqualTo("akasa autonomous ai 2026 update");
    }

    @Test
    void normalizeTitle_collapsesDuplicateSpaces() {
        assertThat(WikiDeduplicationService.normalizeTitle("FDA   AI   Framework"))
                .isEqualTo("fda ai framework");
    }

    @Test
    void normalizeTitle_null_returnsEmpty() {
        assertThat(WikiDeduplicationService.normalizeTitle(null)).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static WikiPageEntity page(String slug, String title, int revision) {
        WikiPageEntity e = new WikiPageEntity();
        e.setSlug(slug);
        e.setTitle(title);
        e.setRevision(revision);
        e.setCreatedAt(Instant.now());
        e.setPageType("ENTITY");
        return e;
    }
}
