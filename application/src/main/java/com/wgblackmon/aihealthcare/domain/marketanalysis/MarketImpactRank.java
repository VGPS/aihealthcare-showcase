package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * Validated market-impact rank for a {@link MarketDigestEntry}, from 1 (highest impact)
 * to 5 (lowest impact).
 *
 * <p>Rank is assigned by the LLM classifier considering stock-price magnitude, company
 * market cap, and whether the news is a near-term catalyst or a long-term structural signal.
 *
 * <p>Note: rank is distinct from confidence — a confirmed SEC filing with modest dollar
 * impact may be high-confidence but rank 4, while an unconfirmed large-acquisition rumor
 * may be low-confidence but rank 1.
 *
 * @param value rank between 1 (highest market impact) and 5 (lowest market impact), inclusive
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record MarketImpactRank(int value) {

    public MarketImpactRank {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("rank must be between 1 and 5, got: " + value);
        }
    }
}
