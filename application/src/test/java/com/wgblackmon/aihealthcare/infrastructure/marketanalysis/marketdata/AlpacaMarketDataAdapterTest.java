package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.marketdata;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceBar;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceHistory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlpacaMarketDataAdapter}.
 *
 * <p>Tests the no-credentials guard, the {@code toQuote} conversion logic,
 * and HTTP error handling. The RestClient is mocked where needed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class AlpacaMarketDataAdapterTest {

    // ─── no credentials guard ────────────────────────────────────────────────

    @Test
    void getQuote_whenApiKeyBlank_returnsEmpty() {
        AlpacaMarketDataAdapter adapter =
                new AlpacaMarketDataAdapter("", "secret", mock(RestClient.class));

        Optional<Quote> result = adapter.getQuote("NVDA");

        assertThat(result).isEmpty();
    }

    @Test
    void getQuote_whenSecretKeyBlank_returnsEmpty() {
        AlpacaMarketDataAdapter adapter =
                new AlpacaMarketDataAdapter("key", "", mock(RestClient.class));

        Optional<Quote> result = adapter.getQuote("NVDA");

        assertThat(result).isEmpty();
    }

    @Test
    void getPriceHistory_whenApiKeyBlank_returnsEmpty() {
        AlpacaMarketDataAdapter adapter =
                new AlpacaMarketDataAdapter("", "secret", mock(RestClient.class));

        Optional<PriceHistory> result = adapter.getPriceHistory(
                "NVDA", LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).isEmpty();
    }

    // ─── toQuote conversion ───────────────────────────────────────────────────

    @Test
    void toQuote_withValidSnapshot_computesChangePercent() {
        AlpacaMarketDataAdapter adapter =
                new AlpacaMarketDataAdapter("key", "secret", mock(RestClient.class));

        AlpacaQuoteResponse.LatestTrade trade = new AlpacaQuoteResponse.LatestTrade(
                new BigDecimal("110.00"));
        AlpacaQuoteResponse.DailyBar prevBar = new AlpacaQuoteResponse.DailyBar(
                null, null, null, new BigDecimal("100.00"), 0L);
        AlpacaQuoteResponse snapshot = new AlpacaQuoteResponse(trade, null, prevBar);

        Quote result = adapter.toQuote("NVDA", snapshot);

        assertThat(result).isNotNull();
        assertThat(result.tickerSymbol()).isEqualTo("NVDA");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("110.00"));
        assertThat(result.changePercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(result.marketCap()).isNull();
    }

    @Test
    void toQuote_whenNoLatestTrade_returnsNull() {
        AlpacaMarketDataAdapter adapter =
                new AlpacaMarketDataAdapter("key", "secret", mock(RestClient.class));

        AlpacaQuoteResponse snapshot = new AlpacaQuoteResponse(null, null, null);

        Quote result = adapter.toQuote("NVDA", snapshot);

        assertThat(result).isNull();
    }

    // ─── HTTP error resilience ────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getPriceHistory_onHttpError_returnsEmpty() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec headersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec       = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec            = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(headersUriSpec);
        when(headersUriSpec.uri(anyString(), (Object[]) any())).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(AlpacaBarsResponse.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        AlpacaMarketDataAdapter adapter =
                new AlpacaMarketDataAdapter("key", "secret", restClient);

        Optional<PriceHistory> result = adapter.getPriceHistory(
                "UNKNOWN", LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).isEmpty();
    }
}
