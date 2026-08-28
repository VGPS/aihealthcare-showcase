package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for the {@code price_reaction_snapshot} table — one row per
 * (entry, horizon) price-reaction measurement.
 *
 * <p>Mirrors {@link GuidanceHistoryEntity}'s shape: an application-generated
 * identity column, no relationship mapping to {@link MarketDigestEntryEntity}
 * (joined manually by {@code entry_id} where needed).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
@Entity
@Table(name = "price_reaction_snapshot")
public class PriceReactionSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", nullable = false, length = 36)
    private String entryId;

    @Column(name = "ticker_symbol", nullable = false, length = 16)
    private String tickerSymbol;

    @Column(name = "horizon", nullable = false, length = 16)
    private String horizon;

    @Column(name = "baseline_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal baselinePrice;

    @Column(name = "observed_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal observedPrice;

    @Column(name = "pct_change", nullable = false, precision = 10, scale = 2)
    private BigDecimal pctChange;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    protected PriceReactionSnapshotEntity() {}

    public PriceReactionSnapshotEntity(String entryId, String tickerSymbol, String horizon,
                                        BigDecimal baselinePrice, BigDecimal observedPrice,
                                        BigDecimal pctChange, Instant measuredAt) {
        this.entryId = entryId;
        this.tickerSymbol = tickerSymbol;
        this.horizon = horizon;
        this.baselinePrice = baselinePrice;
        this.observedPrice = observedPrice;
        this.pctChange = pctChange;
        this.measuredAt = measuredAt;
    }

    public Long getId()                    { return id; }
    public String getEntryId()             { return entryId; }
    public String getTickerSymbol()        { return tickerSymbol; }
    public String getHorizon()             { return horizon; }
    public BigDecimal getBaselinePrice()   { return baselinePrice; }
    public BigDecimal getObservedPrice()   { return observedPrice; }
    public BigDecimal getPctChange()       { return pctChange; }
    public Instant getMeasuredAt()         { return measuredAt; }
}
