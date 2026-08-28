package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.time.Instant;

/**
 * Minimal projection of one ticker-bearing {@link AffectedCompany} on a persisted
 * {@link MarketDigestEntry}, used only by the price-reaction pipeline.
 *
 * <p>{@code entryId} only exists once a digest has been persisted — the domain
 * {@link MarketDigestEntry} aggregate itself carries no identifier, so
 * {@link port.PriceReactionPort#findTickerEntriesPublishedAfter} sources this
 * projection from the persistence layer rather than from {@link MarketDigest}.
 *
 * @param entryId       the persisted {@code MarketDigestEntry}'s id (required, non-blank)
 * @param tickerSymbol  exchange ticker symbol (required, non-blank)
 * @param companyName   company display name, for logging/diagnostics (required, non-blank)
 * @param publishedAt   when the underlying news item was published (required, non-null)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
public record TrackedCompanyEntry(
        String entryId,
        String tickerSymbol,
        String companyName,
        Instant publishedAt
) {

    public TrackedCompanyEntry {
        if (entryId == null || entryId.isBlank()) {
            throw new IllegalArgumentException("entryId must not be blank");
        }
        if (tickerSymbol == null || tickerSymbol.isBlank()) {
            throw new IllegalArgumentException("tickerSymbol must not be blank");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName must not be blank");
        }
        if (publishedAt == null) {
            throw new IllegalArgumentException("publishedAt must not be null");
        }
    }
}
