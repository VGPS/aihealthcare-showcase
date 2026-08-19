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
 * JPA entity for the {@code analyst_rating_change} table.
 *
 * <p>Price target columns are nullable — not all rating changes include
 * updated price targets.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(name = "analyst_rating_change")
public class AnalystRatingChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String firm;

    @Column(nullable = false, length = 16)
    private String tickerSymbol;

    @Column(nullable = false, length = 32)
    private String previousRating;

    @Column(nullable = false, length = 32)
    private String newRating;

    @Column(precision = 10, scale = 2)
    private BigDecimal previousPriceTarget;

    @Column(precision = 10, scale = 2)
    private BigDecimal newPriceTarget;

    @Column(nullable = false)
    private Instant changedAt;

    protected AnalystRatingChangeEntity() {}

    public AnalystRatingChangeEntity(String firm, String tickerSymbol,
                                      String previousRating, String newRating,
                                      BigDecimal previousPriceTarget,
                                      BigDecimal newPriceTarget, Instant changedAt) {
        this.firm                = firm;
        this.tickerSymbol        = tickerSymbol;
        this.previousRating      = previousRating;
        this.newRating           = newRating;
        this.previousPriceTarget = previousPriceTarget;
        this.newPriceTarget      = newPriceTarget;
        this.changedAt           = changedAt;
    }

    public Long getId()                        { return id; }
    public String getFirm()                    { return firm; }
    public String getTickerSymbol()            { return tickerSymbol; }
    public String getPreviousRating()          { return previousRating; }
    public String getNewRating()               { return newRating; }
    public BigDecimal getPreviousPriceTarget() { return previousPriceTarget; }
    public BigDecimal getNewPriceTarget()      { return newPriceTarget; }
    public Instant getChangedAt()              { return changedAt; }
}
