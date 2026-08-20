package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for a secondary, symbol-tagged news source used to cross-check
 * the tracked-ticker list against same-day headlines that the primary
 * {@link MarketNewsResearchPort} Perplexity search adapter may have missed.
 *
 * <p>Implementations must return an empty list (never throw) when the provider
 * is unavailable — a failed secondary check must never fail the whole digest run.
 *
 * <p>Implementations resolve the tracked-ticker list internally from configuration
 * so callers need not know which symbols to watch.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface SecondaryNewsCheckPort {

    /**
     * Fetches recent news articles from the secondary source for all configured
     * tracked tickers, published in the time window [{@code since}, {@code until}].
     *
     * @param since  start of the time window, inclusive (non-null)
     * @param until  end of the time window, inclusive (non-null)
     * @return list of news items; empty list on provider failure or no results
     */
    List<MarketNewsItem> findRecentNews(Instant since, Instant until);
}
