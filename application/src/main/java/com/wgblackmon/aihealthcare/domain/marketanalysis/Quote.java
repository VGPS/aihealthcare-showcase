package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.math.BigDecimal;

/**
 * Real-time market quote for a publicly traded company, returned by {@link port.MarketDataPort}.
 *
 * <p>Sourced from the Alpaca Market Data API (IEX real-time feed). Note that Alpaca does not
 * expose market capitalisation, so {@code marketCap} is always null from the Alpaca adapter;
 * a future adapter may populate it from a secondary source.
 *
 * @param tickerSymbol  exchange ticker symbol (required, non-blank)
 * @param price         last trade price (required, non-null)
 * @param changePercent percentage change from the prior close, e.g. 3.75 for +3.75% (required, non-null)
 * @param marketCap     total market capitalisation in USD (nullable — Alpaca does not provide this)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record Quote(
        String tickerSymbol,
        BigDecimal price,
        BigDecimal changePercent,
        BigDecimal marketCap
) {

    public Quote {
        if (tickerSymbol == null || tickerSymbol.isBlank()) {
            throw new IllegalArgumentException("tickerSymbol must not be blank");
        }
        if (price == null) {
            throw new IllegalArgumentException("price must not be null");
        }
        if (changePercent == null) {
            throw new IllegalArgumentException("changePercent must not be null");
        }
    }
}
