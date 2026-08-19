package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single OHLCV (open/high/low/close/volume) price bar for one trading day.
 *
 * <p>Produced by {@link port.MarketDataPort#getPriceHistory} and collected into
 * a {@link PriceHistory} for a given ticker and date range.
 *
 * @param date   trading date (required, non-null)
 * @param open   opening price (required, non-null)
 * @param high   session high (required, non-null)
 * @param low    session low (required, non-null)
 * @param close  closing price (required, non-null)
 * @param volume shares traded (required, non-negative)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record PriceBar(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {

    public PriceBar {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (open == null) {
            throw new IllegalArgumentException("open must not be null");
        }
        if (high == null) {
            throw new IllegalArgumentException("high must not be null");
        }
        if (low == null) {
            throw new IllegalArgumentException("low must not be null");
        }
        if (close == null) {
            throw new IllegalArgumentException("close must not be null");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }
}
