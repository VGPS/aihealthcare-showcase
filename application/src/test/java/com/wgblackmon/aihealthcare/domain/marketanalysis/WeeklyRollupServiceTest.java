package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WeeklyRollupService}.
 *
 * <p>Verifies headline-based dedup collapse, best-rank election,
 * CONFIRMED-overrides-SPECULATIVE logic, and sort ordering.
 * Uses a null {@link com.wgblackmon.aihealthcare.domain.marketanalysis.port.EntryEmbeddingPort}
 * so every run exercises the headline-collapse path.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-20
 * @updated 2026-08-20
 */
@ExtendWith(MockitoExtension.class)
class WeeklyRollupServiceTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 8, 17);

    @Mock
    private MarketDigestRepository repository;

    private WeeklyRollupService service;

    @BeforeEach
    void setUp() {
        // null embeddingPort → headline-collapse path
        service = new WeeklyRollupService(repository, null, 0.93);
    }

    @Test
    void emptyDigestList_returnsEmptyRollup() {
        when(repository.findByDateRange(WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of());

        WeeklyRollup rollup = service.buildRollup(WEEK_START);

        assertThat(rollup.entries()).isEmpty();
        assertThat(rollup.weekStart()).isEqualTo(WEEK_START);
    }

    @Test
    void singleEntry_passesThrough() {
        MarketDigestEntry entry = makeEntry("DOCS earnings beat", new MarketImpactRank(2), FactClassification.CONFIRMED);
        MarketDigest digest = new MarketDigest(WEEK_START, List.of(entry), Instant.now());
        when(repository.findByDateRange(WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(digest));

        WeeklyRollup rollup = service.buildRollup(WEEK_START);

        assertThat(rollup.entries()).hasSize(1);
        assertThat(rollup.entries().get(0).occurrenceCount()).isEqualTo(1);
    }

    @Test
    void identicalHeadlines_collapsedToOneEntry() {
        MarketDigestEntry e1 = makeEntry("DOCS earnings beat", new MarketImpactRank(2), FactClassification.CONFIRMED);
        MarketDigestEntry e2 = makeEntry("DOCS earnings beat", new MarketImpactRank(3), FactClassification.SPECULATIVE);
        MarketDigest d1 = new MarketDigest(WEEK_START, List.of(e1), Instant.now());
        MarketDigest d2 = new MarketDigest(WEEK_START.plusDays(1), List.of(e2), Instant.now());
        when(repository.findByDateRange(WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(d1, d2));

        WeeklyRollup rollup = service.buildRollup(WEEK_START);

        assertThat(rollup.entries()).hasSize(1);
        assertThat(rollup.entries().get(0).occurrenceCount()).isEqualTo(2);
    }

    @Test
    void bestRankElected_lowestValueWins() {
        // rank 1 is better than rank 3 (lower value = higher priority)
        MarketDigestEntry rank3 = makeEntry("same headline", new MarketImpactRank(3), FactClassification.CONFIRMED);
        MarketDigestEntry rank1 = makeEntry("same headline", new MarketImpactRank(1), FactClassification.CONFIRMED);
        MarketDigest d = new MarketDigest(WEEK_START, List.of(rank3, rank1), Instant.now());
        when(repository.findByDateRange(WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(d));

        WeeklyRollup rollup = service.buildRollup(WEEK_START);

        assertThat(rollup.entries()).hasSize(1);
        assertThat(rollup.entries().get(0).bestRank().value()).isEqualTo(1);
    }

    @Test
    void confirmedOverridesSpeculative() {
        MarketDigestEntry speculative = makeEntry("same headline", new MarketImpactRank(1), FactClassification.SPECULATIVE);
        MarketDigestEntry confirmed   = makeEntry("same headline", new MarketImpactRank(2), FactClassification.CONFIRMED);
        MarketDigest d = new MarketDigest(WEEK_START, List.of(speculative, confirmed), Instant.now());
        when(repository.findByDateRange(WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(d));

        WeeklyRollup rollup = service.buildRollup(WEEK_START);

        assertThat(rollup.entries()).hasSize(1);
        assertThat(rollup.entries().get(0).factClassification()).isEqualTo(FactClassification.CONFIRMED);
    }

    @Test
    void distinctHeadlines_notCollapsed() {
        MarketDigestEntry e1 = makeEntry("DOCS earnings beat", new MarketImpactRank(1), FactClassification.CONFIRMED);
        MarketDigestEntry e2 = makeEntry("EVH acquires startup", new MarketImpactRank(2), FactClassification.CONFIRMED);
        MarketDigest d = new MarketDigest(WEEK_START, List.of(e1, e2), Instant.now());
        when(repository.findByDateRange(WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(d));

        WeeklyRollup rollup = service.buildRollup(WEEK_START);

        assertThat(rollup.entries()).hasSize(2);
    }

    @Test
    void sortedByBestRankAscending() {
        MarketDigestEntry rank3 = makeEntry("story A", new MarketImpactRank(3), FactClassification.CONFIRMED);
        MarketDigestEntry rank1 = makeEntry("story B", new MarketImpactRank(1), FactClassification.CONFIRMED);
        MarketDigest d = new MarketDigest(WEEK_START, List.of(rank3, rank1), Instant.now());
        when(repository.findByDateRange(WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(d));

        WeeklyRollup rollup = service.buildRollup(WEEK_START);

        assertThat(rollup.entries()).hasSize(2);
        assertThat(rollup.entries().get(0).bestRank().value()).isEqualTo(1);
        assertThat(rollup.entries().get(1).bestRank().value()).isEqualTo(3);
    }

    @Test
    void normalizationCollapsesPunctuationDifferences() {
        // "DOCS earnings beat!" and "docs earnings beat" normalize to same key
        MarketDigestEntry e1 = makeEntry("DOCS earnings beat!", new MarketImpactRank(1), FactClassification.CONFIRMED);
        MarketDigestEntry e2 = makeEntry("docs earnings beat",  new MarketImpactRank(2), FactClassification.CONFIRMED);
        MarketDigest d = new MarketDigest(WEEK_START, List.of(e1, e2), Instant.now());
        when(repository.findByDateRange(WEEK_START, WEEK_START.plusDays(6))).thenReturn(List.of(d));

        WeeklyRollup rollup = service.buildRollup(WEEK_START);

        assertThat(rollup.entries()).hasSize(1);
        assertThat(rollup.entries().get(0).occurrenceCount()).isEqualTo(2);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private MarketDigestEntry makeEntry(String headline, MarketImpactRank rank, FactClassification fc) {
        MarketNewsItem item = new MarketNewsItem(
                headline, "Some summary.", List.of("https://example.com"),
                Instant.now(), NewsCategory.EARNINGS, null);
        return new MarketDigestEntry(
                item,
                List.of(new ImpactAssessment(ImpactDimension.REVENUE, ImpactDirection.POSITIVE, "Good")),
                fc,
                rank,
                List.of());
    }
}
