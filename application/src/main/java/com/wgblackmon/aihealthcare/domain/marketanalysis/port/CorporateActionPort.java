package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;

import java.time.LocalDate;

/**
 * Outbound port for attaching corporate action confirmations to a saved digest.
 *
 * <p>Called by {@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService}
 * immediately after the digest is persisted. Implementations query Alpaca's
 * Corporate Actions API for tickers referenced in the digest's affected companies,
 * then persist any matching events as
 * {@link com.wgblackmon.aihealthcare.domain.marketanalysis.CorporateActionConfirmation}
 * records linked by {@code entryId}.
 *
 * <p>This is an optional outbound port — the service accepts a nullable implementation.
 * If the corporate-action adapter is not configured, the pipeline runs without
 * confirmation enrichment.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface CorporateActionPort {

    /**
     * Queries Alpaca Corporate Actions for all tickers in the digest's affected
     * companies, then saves confirmations for any actions within ±3 days of
     * {@code queryDate}.
     *
     * @param digest    the fully built and persisted daily digest (non-null)
     * @param queryDate the digest date used to center the ±3-day window (non-null)
     */
    void attachConfirmations(MarketDigest digest, LocalDate queryDate);
}
