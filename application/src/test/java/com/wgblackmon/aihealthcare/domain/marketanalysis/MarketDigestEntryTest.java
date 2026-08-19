package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MarketDigestEntry} validation, custom accessor delegation,
 * and defensive copy behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class MarketDigestEntryTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validEntry_createsSuccessfully() {
        MarketDigestEntry entry = makeEntry(NewsCategory.EARNINGS, null);
        assertThat(entry.factClassification()).isEqualTo(FactClassification.CONFIRMED);
        assertThat(entry.rank().value()).isEqualTo(1);
    }

    @Test
    void nullNewsItem_throws() {
        assertThatThrownBy(() -> new MarketDigestEntry(
                null,
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(1),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newsItem");
    }

    @Test
    void nullFactClassification_throws() {
        assertThatThrownBy(() -> new MarketDigestEntry(
                makeNewsItem(NewsCategory.EARNINGS, null),
                List.of(),
                null,
                new MarketImpactRank(1),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("factClassification");
    }

    @Test
    void nullRank_throws() {
        assertThatThrownBy(() -> new MarketDigestEntry(
                makeNewsItem(NewsCategory.EARNINGS, null),
                List.of(),
                FactClassification.CONFIRMED,
                null,
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rank");
    }

    @Test
    void category_delegatesToNewsItem() {
        MarketDigestEntry entry = makeEntry(NewsCategory.REGULATORY, null);
        assertThat(entry.category()).isEqualTo(NewsCategory.REGULATORY);
    }

    @Test
    void dealSizeUsd_returnsZeroWhenNewsItemDealSizeIsNull() {
        MarketDigestEntry entry = makeEntry(NewsCategory.FUNDING, null);
        assertThat(entry.dealSizeUsd()).isEqualTo(0L);
    }

    @Test
    void dealSizeUsd_returnsPopulatedValue() {
        MarketDigestEntry entry = makeEntry(NewsCategory.FUNDING, 80_000_000L);
        assertThat(entry.dealSizeUsd()).isEqualTo(80_000_000L);
    }

    @Test
    void affectedCompanies_areDefensivelyCopied() {
        AffectedCompany company = new AffectedCompany("Doximity", "DOCS", "earnings subject", PeerGroup.AI_SCRIBE_DOCUMENTATION);
        List<AffectedCompany> mutable = new ArrayList<>(List.of(company));
        MarketDigestEntry entry = new MarketDigestEntry(
                makeNewsItem(NewsCategory.EARNINGS, null),
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(1),
                mutable
        );
        mutable.add(new AffectedCompany("Injected", null, "role", null));
        assertThat(entry.affectedCompanies()).hasSize(1);
    }

    // --- helpers ---

    private MarketDigestEntry makeEntry(NewsCategory category, Long dealSizeUsd) {
        return new MarketDigestEntry(
                makeNewsItem(category, dealSizeUsd),
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(1),
                List.of()
        );
    }

    private MarketNewsItem makeNewsItem(NewsCategory category, Long dealSizeUsd) {
        return new MarketNewsItem(
                "Test headline",
                "Test summary.",
                List.of("https://example.com"),
                NOW,
                category,
                dealSizeUsd
        );
    }
}
