package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.alpaca;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlpacaNewsAdapter}.
 *
 * <p>Uses the package-private constructor that accepts a pre-built
 * {@link RestClient} stub so no real network calls are made.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
class AlpacaNewsAdapterTest {

    private static final String KEY    = "PKTEST123";
    private static final String SECRET = "supersecret";
    private static final String TICKERS = "DOCS,EVH";

    private static final Instant SINCE = Instant.parse("2026-08-18T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-08-19T00:00:00Z");

    // ─── missing credentials — disabled path ────────────────────────────────

    @Test
    void missingApiKey_returnsEmptyList() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter("", SECRET, TICKERS, mock(RestClient.class));
        List<MarketNewsItem> result = adapter.findRecentNews(SINCE, UNTIL);
        assertThat(result).isEmpty();
    }

    @Test
    void missingSecretKey_returnsEmptyList() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, "", TICKERS, mock(RestClient.class));
        List<MarketNewsItem> result = adapter.findRecentNews(SINCE, UNTIL);
        assertThat(result).isEmpty();
    }

    @Test
    void emptyTickersList_returnsEmptyList() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, "", mock(RestClient.class));
        List<MarketNewsItem> result = adapter.findRecentNews(SINCE, UNTIL);
        assertThat(result).isEmpty();
    }

    // ─── toMarketNewsItem mapping ────────────────────────────────────────────

    @Test
    void toMarketNewsItem_validArticle_mapsCorrectly() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, TICKERS, mock(RestClient.class));
        AlpacaNewsResponse.AlpacaNewsArticle article = new AlpacaNewsResponse.AlpacaNewsArticle(
                999L,
                "Doximity Reports Strong Q2 Earnings",
                "Doximity beat analyst estimates...",
                "https://www.benzinga.com/story",
                List.of("DOCS"),
                Instant.parse("2026-08-19T14:00:00Z")
        );

        MarketNewsItem item = adapter.toMarketNewsItem(article);

        assertThat(item).isNotNull();
        assertThat(item.headline()).isEqualTo("Doximity Reports Strong Q2 Earnings");
        assertThat(item.summary()).isEqualTo("Doximity beat analyst estimates...");
        assertThat(item.sourceUrls()).containsExactly("https://www.benzinga.com/story");
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-08-19T14:00:00Z"));
        assertThat(item.dealSizeUsd()).isNull();
    }

    @Test
    void toMarketNewsItem_nullSummary_usesHeadlineAsFallback() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, TICKERS, mock(RestClient.class));
        AlpacaNewsResponse.AlpacaNewsArticle article = new AlpacaNewsResponse.AlpacaNewsArticle(
                1L, "Headline Only", null, "https://example.com",
                List.of("DOCS"), Instant.now()
        );

        MarketNewsItem item = adapter.toMarketNewsItem(article);

        assertThat(item).isNotNull();
        assertThat(item.summary()).isEqualTo("Headline Only");
    }

    @Test
    void toMarketNewsItem_blankHeadline_returnsNull() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, TICKERS, mock(RestClient.class));
        AlpacaNewsResponse.AlpacaNewsArticle article = new AlpacaNewsResponse.AlpacaNewsArticle(
                2L, "   ", "Summary", "https://example.com", List.of(), Instant.now()
        );

        assertThat(adapter.toMarketNewsItem(article)).isNull();
    }

    @Test
    void toMarketNewsItem_blankUrl_returnsNull() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, TICKERS, mock(RestClient.class));
        AlpacaNewsResponse.AlpacaNewsArticle article = new AlpacaNewsResponse.AlpacaNewsArticle(
                3L, "Valid Headline", "Summary", "  ", List.of(), Instant.now()
        );

        assertThat(adapter.toMarketNewsItem(article)).isNull();
    }

    // ─── inferCategory ───────────────────────────────────────────────────────

    @Test
    void inferCategory_acquisitionKeyword_returnsMAndA() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, TICKERS, mock(RestClient.class));
        assertThat(adapter.inferCategory("Epic Systems Acquires HealthTech Startup"))
                .isEqualTo(NewsCategory.M_AND_A);
    }

    @Test
    void inferCategory_earningsKeyword_returnsEarnings() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, TICKERS, mock(RestClient.class));
        assertThat(adapter.inferCategory("Doximity Q2 Earnings Beat Expectations"))
                .isEqualTo(NewsCategory.EARNINGS);
    }

    @Test
    void inferCategory_fdaKeyword_returnsRegulatory() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, TICKERS, mock(RestClient.class));
        assertThat(adapter.inferCategory("FDA Grants 510k Clearance for AI Diagnostic Tool"))
                .isEqualTo(NewsCategory.REGULATORY);
    }

    @Test
    void inferCategory_unknownKeyword_returnsOther() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, TICKERS, mock(RestClient.class));
        assertThat(adapter.inferCategory("Company Announces New Board Member"))
                .isEqualTo(NewsCategory.OTHER);
    }

    // ─── tracked tickers parsing ─────────────────────────────────────────────

    @Test
    void trackedTickersParsing_commaDelimited_parsedAndUppercased() {
        AlpacaNewsAdapter adapter = new AlpacaNewsAdapter(KEY, SECRET, "docs, evh , ASTH", mock(RestClient.class));
        assertThat(adapter.getTrackedTickers()).containsExactly("DOCS", "EVH", "ASTH");
    }
}
