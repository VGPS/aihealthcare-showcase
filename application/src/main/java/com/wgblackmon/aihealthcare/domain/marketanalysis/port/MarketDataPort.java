package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceHistory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.Quote;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Outbound port for fetching real-time quotes and historical OHLCV data for publicly
 * traded AI-healthcare companies.
 *
 * <p>The Alpaca Market Data adapter ({@code AlpacaMarketDataAdapter}) implements this port
 * using the Alpaca v2 IEX real-time feed (free tier, no daily cap). Alpaca does not expose
 * market capitalisation, so {@link Quote#marketCap()} will be null from that adapter.
 *
 * <p>Both methods return {@link Optional#empty()} on provider outage, unknown ticker, or
 * untracked ticker — a market-data hiccup must never fail the whole digest run.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface MarketDataPort {

    /**
     * Returns the latest real-time quote for the given ticker, or empty if the ticker
     * is untracked, unknown, or the provider is unavailable.
     *
     * @param tickerSymbol exchange ticker symbol (non-blank)
     * @return quote wrapped in Optional, or empty
     */
    Optional<Quote> getQuote(String tickerSymbol);

    /**
     * Returns daily OHLCV bars for the given ticker over the specified date range,
     * or empty if the ticker is untracked or the provider is unavailable.
     *
     * @param tickerSymbol exchange ticker symbol (non-blank)
     * @param start        first date in the range, inclusive
     * @param end          last date in the range, inclusive
     * @return price history wrapped in Optional, or empty
     */
    Optional<PriceHistory> getPriceHistory(String tickerSymbol, LocalDate start, LocalDate end);
}
