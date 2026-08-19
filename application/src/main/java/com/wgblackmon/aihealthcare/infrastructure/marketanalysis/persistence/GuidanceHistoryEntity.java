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
 * JPA entity for the {@code guidance_history} table.
 *
 * <p>Each row records one point-in-time guidance comparison for a ticker+metric pair,
 * preserving the full history so callers can retrieve the most recent prior guidance
 * when a new EARNINGS item is processed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(name = "guidance_history")
public class GuidanceHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String tickerSymbol;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal priorGuidanceLow;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal priorGuidanceHigh;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal newGuidanceLow;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal newGuidanceHigh;

    @Column(nullable = false, length = 64)
    private String metric;

    @Column(nullable = false)
    private Instant recordedAt;

    protected GuidanceHistoryEntity() {}

    public GuidanceHistoryEntity(String tickerSymbol,
                                  BigDecimal priorGuidanceLow,
                                  BigDecimal priorGuidanceHigh,
                                  BigDecimal newGuidanceLow,
                                  BigDecimal newGuidanceHigh,
                                  String metric,
                                  Instant recordedAt) {
        this.tickerSymbol     = tickerSymbol;
        this.priorGuidanceLow  = priorGuidanceLow;
        this.priorGuidanceHigh = priorGuidanceHigh;
        this.newGuidanceLow    = newGuidanceLow;
        this.newGuidanceHigh   = newGuidanceHigh;
        this.metric            = metric;
        this.recordedAt        = recordedAt;
    }

    public Long getId()                   { return id; }
    public String getTickerSymbol()       { return tickerSymbol; }
    public BigDecimal getPriorGuidanceLow()  { return priorGuidanceLow; }
    public BigDecimal getPriorGuidanceHigh() { return priorGuidanceHigh; }
    public BigDecimal getNewGuidanceLow()    { return newGuidanceLow; }
    public BigDecimal getNewGuidanceHigh()   { return newGuidanceHigh; }
    public String getMetric()             { return metric; }
    public Instant getRecordedAt()        { return recordedAt; }
}
