package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * JPA entity for the {@code market_digest_affected_company} table.
 *
 * <p>Each {@link MarketDigestEntryEntity} may have zero or more associated companies,
 * keyed by {@code entry_id}. Loaded separately by the adapter — no {@code @OneToMany}.
 *
 * <p>All market-data fields ({@code quote_price}, {@code quote_change_pct},
 * {@code market_cap}) are nullable — they are populated by the Alpaca adapter in
 * Slice 1.7 and will be null until that slice lands.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(name = "market_digest_affected_company")
public class MarketDigestAffectedCompanyEntity {

    @Id
    @Column(name = "company_id", nullable = false, length = 36)
    private String companyId;

    @Column(name = "entry_id", nullable = false, length = 36)
    private String entryId;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    /** Exchange ticker; null for private companies. */
    @Column(name = "ticker_symbol", length = 16)
    private String tickerSymbol;

    @Column(name = "role", nullable = false, length = 64)
    private String role;

    /** Peer group classification; null when not determinable. */
    @Column(name = "peer_group", length = 32)
    private String peerGroup;

    /** Last trade price from Alpaca; null until Slice 1.7 populates it. */
    @Column(name = "quote_price", precision = 12, scale = 2)
    private BigDecimal quotePrice;

    /** Percentage change from prior close; null until Slice 1.7 populates it. */
    @Column(name = "quote_change_pct", precision = 6, scale = 2)
    private BigDecimal quoteChangePct;

    /** Market capitalisation; null — Alpaca does not provide this. */
    @Column(name = "market_cap", precision = 20, scale = 2)
    private BigDecimal marketCap;

    protected MarketDigestAffectedCompanyEntity() {}

    public MarketDigestAffectedCompanyEntity(String companyId, String entryId,
                                              String companyName, String tickerSymbol,
                                              String role, String peerGroup,
                                              BigDecimal quotePrice, BigDecimal quoteChangePct,
                                              BigDecimal marketCap) {
        this.companyId = companyId;
        this.entryId = entryId;
        this.companyName = companyName;
        this.tickerSymbol = tickerSymbol;
        this.role = role;
        this.peerGroup = peerGroup;
        this.quotePrice = quotePrice;
        this.quoteChangePct = quoteChangePct;
        this.marketCap = marketCap;
    }

    public String getCompanyId() { return companyId; }
    public String getEntryId() { return entryId; }
    public String getCompanyName() { return companyName; }
    public String getTickerSymbol() { return tickerSymbol; }
    public String getRole() { return role; }
    public String getPeerGroup() { return peerGroup; }
    public BigDecimal getQuotePrice() { return quotePrice; }
    public BigDecimal getQuoteChangePct() { return quoteChangePct; }
    public BigDecimal getMarketCap() { return marketCap; }
}
