package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Deserialization record for the Alpaca Markets historical bars API response.
 *
 * <p>Maps the JSON returned by
 * {@code GET https://data.alpaca.markets/v2/stocks/{symbol}/bars?timeframe=1Day&start=...&end=...}
 * into typed Java records.
 *
 * <p>Example abbreviated response:
 * <pre>
 * {
 *   "bars": [
 *     { "t": "2026-08-01T04:00:00Z", "o": 140.00, "h": 143.00, "l": 139.50, "c": 142.50, "v": 12345678 },
 *     ...
 *   ],
 *   "symbol": "NVDA",
 *   "next_page_token": null
 * }
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaBarsResponse(
        @JsonProperty("bars")   List<AlpacaBar> bars,
        @JsonProperty("symbol") String          symbol
) {

    /**
     * A single OHLCV daily bar from the Alpaca historical data API.
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-08-19
     * @updated 2026-08-19
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlpacaBar(
            @JsonProperty("t") Instant    timestamp,
            @JsonProperty("o") BigDecimal open,
            @JsonProperty("h") BigDecimal high,
            @JsonProperty("l") BigDecimal low,
            @JsonProperty("c") BigDecimal close,
            @JsonProperty("v") long       volume
    ) {}
}
