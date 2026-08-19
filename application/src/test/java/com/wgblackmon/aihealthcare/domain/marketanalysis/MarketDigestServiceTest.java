package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link MarketDigestService} — qualifying-bar filter and sort-by-rank logic.
 *
 * <p>Ports are hand-rolled stubs (no-op implementations) — no real adapters exist yet.
 * The full pipeline (generateDailyDigest) is not tested here; it is covered in Slice 1.6.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class MarketDigestServiceTest {

    private MarketDigestService service;

    @BeforeEach
    void setUp() {
        service = new MarketDigestService(
                mock(MarketNewsResearchPort.class),
                mock(MarketDataPort.class),
                mock(ImpactClassifierPort.class),
                mock(MarketDigestRepository.class),
                mock(MarketDigestNotifier.class),
                null,
                0.93
        );
    }

    // --- isMarketMoving: always-qualifying categories ---

    @Test
    void earnings_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.EARNINGS, null, 1))).isTrue();
    }

    @Test
    void regulatory_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.REGULATORY, null, 1))).isTrue();
    }

    @Test
    void mAndA_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.M_AND_A, null, 1))).isTrue();
    }

    @Test
    void majorPartnership_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.MAJOR_PARTNERSHIP, null, 1))).isTrue();
    }

    @Test
    void other_doesNotQualify() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.OTHER, null, 1))).isFalse();
    }

    // --- isMarketMoving: FUNDING boundary cases ---

    @Test
    void funding_aboveThreshold_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.FUNDING, 50_000_001L, 1))).isTrue();
    }

    @Test
    void funding_atExactThreshold_doesNotQualify() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.FUNDING, 50_000_000L, 1))).isFalse();
    }

    @Test
    void funding_belowThreshold_doesNotQualify() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.FUNDING, 49_999_999L, 1))).isFalse();
    }

    @Test
    void funding_nullDealSize_doesNotQualify() {
        // null dealSizeUsd → dealSizeUsd() returns 0L → does not qualify
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.FUNDING, null, 1))).isFalse();
    }

    // --- filterAndSort ---

    @Test
    void filterAndSort_onlyReturnsQualifyingEntries() {
        List<MarketDigestEntry> entries = List.of(
                makeEntry(NewsCategory.EARNINGS, null, 2),
                makeEntry(NewsCategory.OTHER, null, 1),
                makeEntry(NewsCategory.REGULATORY, null, 3),
                makeEntry(NewsCategory.FUNDING, 40_000_000L, 1)  // below threshold
        );
        List<MarketDigestEntry> result = service.filterAndSort(entries);
        assertThat(result).hasSize(2);
        for (MarketDigestEntry e : result) {
            assertThat(e.category()).isIn(NewsCategory.EARNINGS, NewsCategory.REGULATORY);
        }
    }

    @Test
    void filterAndSort_sortsByRankAscending() {
        List<MarketDigestEntry> entries = List.of(
                makeEntry(NewsCategory.EARNINGS, null, 3),
                makeEntry(NewsCategory.REGULATORY, null, 1),
                makeEntry(NewsCategory.M_AND_A, null, 2)
        );
        List<MarketDigestEntry> result = service.filterAndSort(entries);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).rank().value()).isEqualTo(1);
        assertThat(result.get(1).rank().value()).isEqualTo(2);
        assertThat(result.get(2).rank().value()).isEqualTo(3);
    }

    @Test
    void filterAndSort_emptyInput_returnsEmpty() {
        assertThat(service.filterAndSort(List.of())).isEmpty();
    }

    // --- helper ---

    private static MarketDigestEntry makeEntry(NewsCategory category, Long dealSizeUsd, int rank) {
        MarketNewsItem item = new MarketNewsItem(
                "Headline",
                "Summary.",
                List.of("https://example.com"),
                Instant.now(),
                category,
                dealSizeUsd
        );
        return new MarketDigestEntry(
                item,
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(rank),
                List.of()
        );
    }
}
