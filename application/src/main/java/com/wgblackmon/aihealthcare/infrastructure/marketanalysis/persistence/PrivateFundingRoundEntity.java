package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for the {@code private_funding_round} table.
 *
 * <p>{@code leadInvestors} is stored pipe-delimited (matching the convention
 * used elsewhere in this project for {@code List<String>} columns).
 * {@code peerGroup} is the string name of the resolved {@code PeerGroup} enum value,
 * assigned by {@code PeerGroupTagger} at save time.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(name = "private_funding_round")
public class PrivateFundingRoundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String companyName;

    @Column(nullable = false, length = 64)
    private String roundStage;

    @Column
    private Long amountUsd;

    @Column(columnDefinition = "TEXT")
    private String leadInvestors;

    @Column(nullable = false)
    private Instant announcedAt;

    @Column(nullable = false, length = 64)
    private String peerGroup;

    protected PrivateFundingRoundEntity() {}

    public PrivateFundingRoundEntity(String companyName, String roundStage,
                                      Long amountUsd, String leadInvestors,
                                      Instant announcedAt, String peerGroup) {
        this.companyName    = companyName;
        this.roundStage     = roundStage;
        this.amountUsd      = amountUsd;
        this.leadInvestors  = leadInvestors;
        this.announcedAt    = announcedAt;
        this.peerGroup      = peerGroup;
    }

    public Long getId()               { return id; }
    public String getCompanyName()    { return companyName; }
    public String getRoundStage()     { return roundStage; }
    public Long getAmountUsd()        { return amountUsd; }
    public String getLeadInvestors()  { return leadInvestors; }
    public Instant getAnnouncedAt()   { return announcedAt; }
    public String getPeerGroup()      { return peerGroup; }
}
