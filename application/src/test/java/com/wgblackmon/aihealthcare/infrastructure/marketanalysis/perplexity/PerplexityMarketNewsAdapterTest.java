package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.perplexity;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PerplexityMarketNewsAdapter}.
 *
 * <p>Uses a mocked {@link RestClient} chain so no real HTTP calls are made.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class PerplexityMarketNewsAdapterTest {

    private PromptLoaderService promptLoader;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec    bodySpec;
    private RestClient.ResponseSpec       responseSpec;

    private static final String PROMPT_TEMPLATE = "Find market news after {since_date}. Output ITEM blocks.";

    @BeforeEach
    void setUp() {
        promptLoader = mock(PromptLoaderService.class);
        restClient   = mock(RestClient.class);
        uriSpec      = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec     = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(promptLoader.load("market-news-research.txt")).thenReturn(PROMPT_TEMPLATE);
    }

    private void stubApiResponse(String content) {
        PerplexityApiResponse.PerplexityMessage msg =
                new PerplexityApiResponse.PerplexityMessage("assistant", content);
        PerplexityApiResponse.PerplexityChoice choice =
                new PerplexityApiResponse.PerplexityChoice(msg);
        PerplexityApiResponse apiResponse =
                new PerplexityApiResponse("resp-1", List.of(choice), List.of());

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(PerplexityApiResponse.class)).thenReturn(apiResponse);
    }

    // ─── no API key ───────────────────────────────────────────────────────────

    @Test
    void findRecentAiHealthcareNews_whenApiKeyBlank_returnsEmptyList() {
        PerplexityMarketNewsAdapter adapter =
                new PerplexityMarketNewsAdapter(promptLoader, "", "sonar", restClient);

        List<MarketNewsItem> result = adapter.findRecentAiHealthcareNews(Instant.now());

        assertThat(result).isEmpty();
    }

    // ─── single well-formed ITEM block ───────────────────────────────────────

    @Test
    void findRecentAiHealthcareNews_withSingleItem_parsesAllFields() {
        String response = """
                ITEM
                HEADLINE: Epic acquires AI diagnostics startup for $200M
                SUMMARY: Epic Systems agreed to acquire an AI diagnostics startup in an all-cash deal valued at $200 million.
                CATEGORY: M_AND_A
                DEAL_SIZE_USD: 200000000
                PUBLISHED_AT: 2026-08-19T10:00:00Z
                SOURCES: https://healthcare.com/epic-deal|https://fiercehealthcare.com/epic
                END_ITEM
                """;
        stubApiResponse(response);

        PerplexityMarketNewsAdapter adapter =
                new PerplexityMarketNewsAdapter(promptLoader, "test-key", "sonar", restClient);
        List<MarketNewsItem> result = adapter.findRecentAiHealthcareNews(Instant.now());

        assertThat(result).hasSize(1);
        MarketNewsItem item = result.get(0);
        assertThat(item.headline()).isEqualTo("Epic acquires AI diagnostics startup for $200M");
        assertThat(item.summary()).contains("Epic Systems");
        assertThat(item.category()).isEqualTo(NewsCategory.M_AND_A);
        assertThat(item.dealSizeUsd()).isEqualTo(200_000_000L);
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-08-19T10:00:00Z"));
        assertThat(item.sourceUrls()).containsExactly(
                "https://healthcare.com/epic-deal", "https://fiercehealthcare.com/epic");
    }

    // ─── multiple ITEM blocks ─────────────────────────────────────────────────

    @Test
    void findRecentAiHealthcareNews_withMultipleItems_returnsAll() {
        String response = """
                ITEM
                HEADLINE: Nuance earnings beat Q2 estimates
                SUMMARY: Nuance reported Q2 revenue of $420M, beating analyst estimates by 8%.
                CATEGORY: EARNINGS
                DEAL_SIZE_USD: NONE
                PUBLISHED_AT: 2026-08-18T14:30:00Z
                SOURCES: https://wsj.com/nuance-q2
                END_ITEM
                ITEM
                HEADLINE: FDA clears AI-powered ECG analysis tool
                SUMMARY: The FDA granted 510(k) clearance to CardioAI's ECG interpretation algorithm.
                CATEGORY: REGULATORY
                DEAL_SIZE_USD: NONE
                PUBLISHED_AT: 2026-08-17T09:00:00Z
                SOURCES: https://fda.gov/clearances/cardioai
                END_ITEM
                """;
        stubApiResponse(response);

        PerplexityMarketNewsAdapter adapter =
                new PerplexityMarketNewsAdapter(promptLoader, "test-key", "sonar", restClient);
        List<MarketNewsItem> result = adapter.findRecentAiHealthcareNews(Instant.now());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).category()).isEqualTo(NewsCategory.EARNINGS);
        assertThat(result.get(0).dealSizeUsd()).isNull();
        assertThat(result.get(1).category()).isEqualTo(NewsCategory.REGULATORY);
    }

    // ─── NO_RESULTS sentinel ─────────────────────────────────────────────────

    @Test
    void findRecentAiHealthcareNews_whenNoResults_returnsEmptyList() {
        stubApiResponse("NO_RESULTS");

        PerplexityMarketNewsAdapter adapter =
                new PerplexityMarketNewsAdapter(promptLoader, "test-key", "sonar", restClient);
        List<MarketNewsItem> result = adapter.findRecentAiHealthcareNews(Instant.now());

        assertThat(result).isEmpty();
    }

    // ─── unknown category defaults to OTHER ──────────────────────────────────

    @Test
    void findRecentAiHealthcareNews_withUnknownCategory_defaultsToOther() {
        String response = """
                ITEM
                HEADLINE: Some unusual event
                SUMMARY: Description of some unusual event in healthcare AI.
                CATEGORY: PARTNERSHIP_EXPANDED
                DEAL_SIZE_USD: NONE
                PUBLISHED_AT: UNKNOWN
                SOURCES: https://example.com
                END_ITEM
                """;
        stubApiResponse(response);

        PerplexityMarketNewsAdapter adapter =
                new PerplexityMarketNewsAdapter(promptLoader, "test-key", "sonar", restClient);
        List<MarketNewsItem> result = adapter.findRecentAiHealthcareNews(Instant.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo(NewsCategory.OTHER);
    }

    // ─── UNKNOWN publishedAt falls back to since ──────────────────────────────

    @Test
    void findRecentAiHealthcareNews_withUnknownPublishedAt_usesFallback() {
        String response = """
                ITEM
                HEADLINE: Funding round completed
                SUMMARY: A startup raised $75M in Series B funding.
                CATEGORY: FUNDING
                DEAL_SIZE_USD: 75000000
                PUBLISHED_AT: UNKNOWN
                SOURCES: https://example.com
                END_ITEM
                """;
        stubApiResponse(response);

        Instant since = Instant.parse("2026-08-15T00:00:00Z");
        PerplexityMarketNewsAdapter adapter =
                new PerplexityMarketNewsAdapter(promptLoader, "test-key", "sonar", restClient);
        List<MarketNewsItem> result = adapter.findRecentAiHealthcareNews(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).publishedAt()).isEqualTo(since);
    }

    // ─── null/empty API response ──────────────────────────────────────────────

    @Test
    void findRecentAiHealthcareNews_whenApiReturnsEmpty_returnsEmptyList() {
        PerplexityApiResponse apiResponse =
                new PerplexityApiResponse("resp-x", List.of(), null);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(PerplexityApiResponse.class)).thenReturn(apiResponse);

        PerplexityMarketNewsAdapter adapter =
                new PerplexityMarketNewsAdapter(promptLoader, "test-key", "sonar", restClient);
        List<MarketNewsItem> result = adapter.findRecentAiHealthcareNews(Instant.now());

        assertThat(result).isEmpty();
    }
}
