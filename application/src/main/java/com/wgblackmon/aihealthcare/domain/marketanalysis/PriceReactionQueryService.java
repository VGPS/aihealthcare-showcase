package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PriceReactionPort;

import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

/**
 * Domain service for read-side price-reaction lookups.
 *
 * <p>Thin orchestration layer that decouples web controllers from the
 * {@link PriceReactionPort} outbound port, satisfying the hexagonal-architecture
 * rule that controllers must only call services, never ports directly — the same
 * pattern used by {@link GuidanceQueryService} for {@code GuidancePort}.
 *
 * <p>Pure Java — no Spring or Lombok imports, consistent with domain-module
 * purity constraints. Instantiated as a {@code @Bean} in {@code MarketAnalysisConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
public class PriceReactionQueryService {

    private static final Logger log =
            Logger.getLogger(PriceReactionQueryService.class.getName());

    private final PriceReactionPort reactionPort;

    public PriceReactionQueryService(PriceReactionPort reactionPort) {
        this.reactionPort = reactionPort;
    }

    /**
     * Returns every reaction snapshot captured so far for the digest entry identified
     * by its ticker and news-item publish time, or an empty list if none have been
     * captured yet (e.g. no horizon is due, or the entry has no ticker).
     */
    public List<PriceReactionSnapshot> findReactions(String tickerSymbol, Instant publishedAt) {
        log.fine(() -> "findReactions() | tickerSymbol=" + tickerSymbol + ", publishedAt=" + publishedAt);
        List<PriceReactionSnapshot> result = reactionPort.findByTickerAndPublishedAt(tickerSymbol, publishedAt);
        log.fine(() -> "findReactions() | return.size=" + result.size());
        return result;
    }
}
