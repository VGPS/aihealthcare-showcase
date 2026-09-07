package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code new_bill_candidates} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.NewBillCandidate}
 * domain record. Candidates are discovered by the Perplexity legislation
 * discovery pipeline and require human review before promotion to the
 * legislation registry. The {@code sourceUrls} list is stored as a
 * pipe-delimited string. Conversion is performed inside {@link StateLawAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@Entity
@Table(name = "new_bill_candidates")
public class NewBillCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode;

    @Column(name = "bill_number", nullable = false, length = 50)
    private String billNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source_urls", columnDefinition = "TEXT")
    private String sourceUrls;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "reviewed", nullable = false)
    private boolean reviewed;

    @Column(name = "promoted_law_id", length = 100)
    private String promotedLawId;

    /** Required no-arg constructor for JPA. */
    public NewBillCandidateEntity() {}

    public Long getId()                                         { return id; }
    public void setId(Long id)                                  { this.id = id; }

    public String getStateCode()                                { return stateCode; }
    public void setStateCode(String stateCode)                  { this.stateCode = stateCode; }

    public String getBillNumber()                               { return billNumber; }
    public void setBillNumber(String billNumber)                { this.billNumber = billNumber; }

    public String getTitle()                                    { return title; }
    public void setTitle(String title)                          { this.title = title; }

    public String getSummary()                                  { return summary; }
    public void setSummary(String summary)                      { this.summary = summary; }

    public String getSourceUrls()                               { return sourceUrls; }
    public void setSourceUrls(String sourceUrls)                { this.sourceUrls = sourceUrls; }

    public Instant getDiscoveredAt()                            { return discoveredAt; }
    public void setDiscoveredAt(Instant discoveredAt)           { this.discoveredAt = discoveredAt; }

    public double getConfidence()                               { return confidence; }
    public void setConfidence(double confidence)                { this.confidence = confidence; }

    public boolean isReviewed()                                 { return reviewed; }
    public void setReviewed(boolean reviewed)                   { this.reviewed = reviewed; }

    public String getPromotedLawId()                            { return promotedLawId; }
    public void setPromotedLawId(String promotedLawId)          { this.promotedLawId = promotedLawId; }
}
