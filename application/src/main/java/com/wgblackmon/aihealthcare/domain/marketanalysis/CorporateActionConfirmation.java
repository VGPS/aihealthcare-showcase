package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable domain record representing a confirmed corporate action from Alpaca's
 * Corporate Actions API that matches a ticker referenced in a market digest.
 *
 * <p>Linked to the digest by {@link #digestDate} rather than by an entry-level
 * UUID, because the domain {@link MarketDigestEntry} record does not carry its
 * JPA-generated ID. The ticker symbol together with the date provides a stable
 * natural key for display and deduplication.
 *
 * <p>All date fields except {@link #declarationDate} are nullable because Alpaca's
 * response omits them for certain action types (e.g. mergers).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record CorporateActionConfirmation(
        String    confirmationId,
        LocalDate digestDate,
        String    tickerSymbol,
        String    actionType,
        LocalDate declarationDate,
        LocalDate exDate,
        LocalDate recordDate,
        LocalDate payableDate
) {
    public CorporateActionConfirmation {
        Objects.requireNonNull(confirmationId, "confirmationId is required");
        Objects.requireNonNull(digestDate, "digestDate is required");
        Objects.requireNonNull(tickerSymbol, "tickerSymbol is required");
        Objects.requireNonNull(actionType, "actionType is required");
        Objects.requireNonNull(declarationDate, "declarationDate is required");
        // exDate, recordDate, payableDate are nullable — allowed per Alpaca API variance
    }
}
