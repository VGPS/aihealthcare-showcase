package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Deserialization record for the Alpaca Markets snapshot API response.
 *
 * <p>Maps the JSON returned by
 * {@code GET https://data.alpaca.markets/v2/stocks/snapshots?symbols=TICKER}
 * into typed Java records. The outer map is keyed by ticker symbol;
 * this record represents a single ticker's snapshot value.
 *
 * <p>Example abbreviated response (for one ticker):
 * <pre>
 * {
 *   "latestTrade": { "p": 142.50 },
 *   "dailyBar":    { "c": 142.50, "o": 140.00, "h": 143.00, "l": 139.50, "v": 12345678 },
 *   "prevDailyBar":{ "c": 141.00 }
 * }
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaQuoteResponse(
        @JsonProperty("latestTrade")  LatestTrade latestTrade,
        @JsonProperty("dailyBar")     DailyBar    dailyBar,
        @JsonProperty("prevDailyBar") DailyBar    prevDailyBar
) {

    /**
     * Most recent trade price for the symbol.
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-08-19
     * @updated 2026-08-19
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LatestTrade(
            @JsonProperty("p") BigDecimal price
    ) {}

    /**
     * OHLCV bar for the current or previous trading session.
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-08-19
     * @updated 2026-08-19
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyBar(
            @JsonProperty("o") BigDecimal open,
            @JsonProperty("h") BigDecimal high,
            @JsonProperty("l") BigDecimal low,
            @JsonProperty("c") BigDecimal close,
            @JsonProperty("v") long       volume
    ) {}
}
