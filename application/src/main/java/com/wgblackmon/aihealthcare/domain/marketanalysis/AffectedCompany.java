package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * A company mentioned in a market news item, with optional ticker and peer-group metadata.
 *
 * <p>Public companies carry a {@code tickerSymbol} for market-data enrichment via
 * {@link port.MarketDataPort}. Private companies have a null ticker but may carry
 * a {@code peerGroup} so they can be surfaced as read-through signals alongside their
 * public peers (e.g. Abridge alongside Doximity in AI_SCRIBE_DOCUMENTATION).
 *
 * @param name          company display name (required, non-blank)
 * @param tickerSymbol  exchange ticker symbol (nullable — private companies have no ticker)
 * @param role          role in the news event, e.g. "earnings subject", "acquirer",
 *                      "deal counterparty" (required, non-blank)
 * @param peerGroup     AI-healthcare peer-group classification (nullable — not always determinable)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record AffectedCompany(
        String name,
        String tickerSymbol,
        String role,
        PeerGroup peerGroup
) {

    public AffectedCompany {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }
}
