package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MarketDigest} — the empty() factory, defensive copy behaviour,
 * and equals/hashCode stability across all {@link NewsCategory} values.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class MarketDigestTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);
    private static final Instant NOW = Instant.now();

    @Test
    void empty_producesEmptyEntriesList() {
        MarketDigest digest = MarketDigest.empty(TODAY);
        assertThat(digest.entries()).isEmpty();
    }

    @Test
    void empty_usesGivenDate() {
        MarketDigest digest = MarketDigest.empty(TODAY);
        assertThat(digest.date()).isEqualTo(TODAY);
    }

    @Test
    void nullDate_throws() {
        assertThatThrownBy(() -> new MarketDigest(null, List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void nullGeneratedAt_throws() {
        assertThatThrownBy(() -> new MarketDigest(TODAY, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generatedAt");
    }

    @Test
    void entries_areDefensivelyCopied() {
        List<MarketDigestEntry> mutable = new ArrayList<>(List.of(makeEntry(NewsCategory.EARNINGS)));
        MarketDigest digest = new MarketDigest(TODAY, mutable, NOW);
        mutable.add(makeEntry(NewsCategory.REGULATORY));
        assertThat(digest.entries()).hasSize(1);
    }

    @Test
    void equalsHashCode_acrossAllNewsCategories() {
        for (NewsCategory category : NewsCategory.values()) {
            MarketDigestEntry entry = makeEntry(category);
            MarketDigest digest = new MarketDigest(TODAY, List.of(entry), NOW);
            MarketDigest same = new MarketDigest(TODAY, List.of(entry), digest.generatedAt());
            assertThat(digest).isEqualTo(same);
            assertThat(digest.hashCode()).isEqualTo(same.hashCode());
        }
    }

    // --- helper ---

    private MarketDigestEntry makeEntry(NewsCategory category) {
        MarketNewsItem item = new MarketNewsItem(
                "Headline for " + category,
                "Summary for " + category + " event.",
                List.of("https://example.com/" + category.name().toLowerCase()),
                Instant.now(),
                category,
                category == NewsCategory.FUNDING ? 60_000_000L : null
        );
        return new MarketDigestEntry(
                item,
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(1),
                List.of()
        );
    }
}
