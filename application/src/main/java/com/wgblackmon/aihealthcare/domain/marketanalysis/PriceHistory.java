package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.util.List;

/**
 * Historical OHLCV price series for a publicly traded company over a requested date range.
 *
 * <p>Returned by {@link port.MarketDataPort#getPriceHistory} and sourced from Alpaca's
 * historical bars endpoint. Each element in {@code bars} represents one trading day.
 *
 * @param tickerSymbol  exchange ticker symbol (required, non-blank)
 * @param bars          ordered list of daily OHLCV bars (required; null yields empty list)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record PriceHistory(
        String tickerSymbol,
        List<PriceBar> bars
) {

    public PriceHistory {
        if (tickerSymbol == null || tickerSymbol.isBlank()) {
            throw new IllegalArgumentException("tickerSymbol must not be blank");
        }
        bars = bars == null ? List.of() : List.copyOf(bars);
    }
}
