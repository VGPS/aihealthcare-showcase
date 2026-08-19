package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * JPA entity for the {@code ticker_watchlist} table.
 *
 * <p>Each row represents one ticker on one subscriber's watchlist.
 * The unique constraint prevents duplicate tickers per subscriber.
 * {@code addedAt} preserves insertion order for display.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(
        name = "ticker_watchlist",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_watchlist_subscriber_ticker",
                columnNames = {"subscriber_id", "ticker_symbol"}
        )
)
public class TickerWatchlistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscriber_id", nullable = false, length = 256)
    private String subscriberId;

    @Column(name = "ticker_symbol", nullable = false, length = 16)
    private String tickerSymbol;

    @Column(nullable = false)
    private Instant addedAt;

    protected TickerWatchlistEntity() {}

    public TickerWatchlistEntity(String subscriberId, String tickerSymbol, Instant addedAt) {
        this.subscriberId = subscriberId;
        this.tickerSymbol = tickerSymbol;
        this.addedAt      = addedAt;
    }

    public Long getId()             { return id; }
    public String getSubscriberId() { return subscriberId; }
    public String getTickerSymbol() { return tickerSymbol; }
    public Instant getAddedAt()     { return addedAt; }
}
