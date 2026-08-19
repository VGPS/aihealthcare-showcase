package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for fetching recent AI-healthcare market news from an external research source.
 *
 * <p>The Perplexity Search API adapter ({@code PerplexityMarketNewsAdapter}) implements
 * this port using a recency-filtered search. Uses the Search API (not Deep Research)
 * for daily cadence cost efficiency — Deep Research remains reserved for the weekly
 * Trends pipeline.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface MarketNewsResearchPort {

    /**
     * Returns recent AI-healthcare market news items published or discovered since the
     * given instant. Returns an empty list if nothing is found — never throws on
     * a genuinely empty result set.
     *
     * @param since lower-bound timestamp for the news window (non-null)
     * @return list of news items, possibly empty
     */
    List<MarketNewsItem> findRecentAiHealthcareNews(Instant since);
}
