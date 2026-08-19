package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * JPA entity for the {@code deal_terms} table.
 *
 * <p>Keyed by a natural key ({@code entry_headline}) rather than an FK to
 * {@code market_digest_entry}, avoiding a brittle join dependency across
 * entity tables. All financial fields are nullable to reflect partial disclosure.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(
        name = "deal_terms",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_deal_terms_headline",
                columnNames = "entry_headline_hash"
        )
)
public class DealTermsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_headline_hash", nullable = false, length = 512)
    private String entryHeadline;

    @Column
    private Long upfrontCashUsd;

    @Column
    private Long milestonePaymentsUsd;

    @Column(precision = 6, scale = 2)
    private BigDecimal equityStakePct;

    @Column(precision = 6, scale = 2)
    private BigDecimal royaltyPct;

    @Column(nullable = false, length = 16)
    private String disclosedPortion;

    protected DealTermsEntity() {}

    public DealTermsEntity(String entryHeadline, Long upfrontCashUsd,
                            Long milestonePaymentsUsd, BigDecimal equityStakePct,
                            BigDecimal royaltyPct, String disclosedPortion) {
        this.entryHeadline       = entryHeadline;
        this.upfrontCashUsd      = upfrontCashUsd;
        this.milestonePaymentsUsd = milestonePaymentsUsd;
        this.equityStakePct      = equityStakePct;
        this.royaltyPct          = royaltyPct;
        this.disclosedPortion    = disclosedPortion;
    }

    public Long getId()                    { return id; }
    public String getEntryHeadline()       { return entryHeadline; }
    public Long getUpfrontCashUsd()        { return upfrontCashUsd; }
    public Long getMilestonePaymentsUsd()  { return milestonePaymentsUsd; }
    public BigDecimal getEquityStakePct()  { return equityStakePct; }
    public BigDecimal getRoyaltyPct()      { return royaltyPct; }
    public String getDisclosedPortion()    { return disclosedPortion; }
}
