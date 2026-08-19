package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MarketNewsItem} record validation and defensive copy behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class MarketNewsItemTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validItem_createsSuccessfully() {
        MarketNewsItem item = new MarketNewsItem(
                "FDA clears AI diagnostic",
                "The FDA granted 510(k) clearance to an AI-powered imaging tool.",
                List.of("https://fda.gov/press-release"),
                NOW,
                NewsCategory.REGULATORY,
                null
        );
        assertThat(item.headline()).isEqualTo("FDA clears AI diagnostic");
        assertThat(item.category()).isEqualTo(NewsCategory.REGULATORY);
        assertThat(item.dealSizeUsd()).isNull();
    }

    @Test
    void nullHeadline_throws() {
        assertThatThrownBy(() -> new MarketNewsItem(
                null, "summary", List.of("https://example.com"), NOW, NewsCategory.EARNINGS, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headline");
    }

    @Test
    void blankHeadline_throws() {
        assertThatThrownBy(() -> new MarketNewsItem(
                "  ", "summary", List.of("https://example.com"), NOW, NewsCategory.EARNINGS, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headline");
    }

    @Test
    void nullSummary_throws() {
        assertThatThrownBy(() -> new MarketNewsItem(
                "headline", null, List.of("https://example.com"), NOW, NewsCategory.EARNINGS, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void nullSourceUrls_defaultsToEmptyList() {
        MarketNewsItem item = new MarketNewsItem(
                "headline", "summary", null, NOW, NewsCategory.EARNINGS, null
        );
        assertThat(item.sourceUrls()).isEmpty();
    }

    @Test
    void sourceUrls_areDefensivelyCopied() {
        List<String> mutableUrls = new ArrayList<>(List.of("https://example.com"));
        MarketNewsItem item = new MarketNewsItem(
                "headline", "summary", mutableUrls, NOW, NewsCategory.EARNINGS, null
        );
        mutableUrls.add("https://injected.com");
        assertThat(item.sourceUrls()).hasSize(1);
    }

    @Test
    void nullPublishedAt_throws() {
        assertThatThrownBy(() -> new MarketNewsItem(
                "headline", "summary", List.of("https://example.com"), null, NewsCategory.EARNINGS, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishedAt");
    }

    @Test
    void nullCategory_throws() {
        assertThatThrownBy(() -> new MarketNewsItem(
                "headline", "summary", List.of("https://example.com"), NOW, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");
    }

    @Test
    void dealSizeUsd_canBeNull() {
        MarketNewsItem item = new MarketNewsItem(
                "headline", "summary", List.of("https://example.com"), NOW, NewsCategory.FUNDING, null
        );
        assertThat(item.dealSizeUsd()).isNull();
    }

    @Test
    void dealSizeUsd_canBePopulated() {
        MarketNewsItem item = new MarketNewsItem(
                "headline", "summary", List.of("https://example.com"), NOW, NewsCategory.FUNDING, 75_000_000L
        );
        assertThat(item.dealSizeUsd()).isEqualTo(75_000_000L);
    }
}
