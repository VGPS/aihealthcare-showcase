package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;

/**
 * Outbound port for notifying subscribers when a qualifying daily market digest is ready.
 *
 * <p>The SES adapter ({@code SesMarketDigestNotifier}) implements this port. This method
 * is called by {@code MarketDigestService} only when the digest has at least one qualifying
 * entry — it is never invoked for empty digests.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface MarketDigestNotifier {

    /**
     * Delivers the digest to all active market-alert subscribers. Implementations must
     * not throw — any delivery failure should be caught, logged, and recovered gracefully
     * so a single bad subscriber address never aborts the notification run.
     *
     * @param digest the qualifying digest to deliver (non-null, non-empty entries)
     */
    void notify(MarketDigest digest);
}
