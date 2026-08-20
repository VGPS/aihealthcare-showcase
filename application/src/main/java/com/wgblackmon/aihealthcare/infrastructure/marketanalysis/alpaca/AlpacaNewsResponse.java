package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.alpaca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Deserialization record for the Alpaca Markets News API response.
 *
 * <p>Maps the JSON returned by
 * {@code GET https://data.alpaca.markets/v1beta1/news?symbols=...&start=...&end=...}
 * into typed Java records.
 *
 * <p>Example abbreviated response:
 * <pre>
 * {
 *   "news": [
 *     {
 *       "id": 123456,
 *       "headline": "Doximity Reports Strong Q2 Earnings",
 *       "summary": "Doximity beat analyst estimates by ...",
 *       "url": "https://www.benzinga.com/...",
 *       "symbols": ["DOCS"],
 *       "updated_at": "2026-08-19T14:30:00Z"
 *     }
 *   ],
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
public record AlpacaNewsResponse(
        @JsonProperty("news") List<AlpacaNewsArticle> news
) {

    /**
     * A single news article from the Alpaca News API (Benzinga-sourced).
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-08-19
     * @updated 2026-08-19
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlpacaNewsArticle(
            @JsonProperty("id")         long         id,
            @JsonProperty("headline")   String       headline,
            @JsonProperty("summary")    String       summary,
            @JsonProperty("url")        String       url,
            @JsonProperty("symbols")    List<String> symbols,
            @JsonProperty("updated_at") Instant      updatedAt
    ) {}
}
