package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity backing the {@code frontier_claims} table.
 *
 * <p>Enum values ({@code claimType}, {@code verdict}) are stored as their
 * {@code name()} string.  Nullable fields ({@code claimDate}, {@code sourceUrl},
 * {@code sourceTitle}, {@code evidenceNotes}, {@code articleId},
 * {@code lastReviewedAt}) are mapped with {@code nullable = true}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@Entity
@Table(name = "frontier_claims")
public class FrontierClaimEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "company", nullable = false)
    private String company;

    @Column(name = "claim_text", nullable = false, columnDefinition = "TEXT")
    private String claimText;

    @Column(name = "claim_date", nullable = true)
    private LocalDate claimDate;

    @Column(name = "source_url", nullable = true, length = 2048)
    private String sourceUrl;

    @Column(name = "source_title", nullable = true)
    private String sourceTitle;

    @Column(name = "claim_type", nullable = false)
    private String claimType;

    @Column(name = "verdict", nullable = false)
    private String verdict;

    @Column(name = "evidence_notes", nullable = true, columnDefinition = "TEXT")
    private String evidenceNotes;

    @Column(name = "article_id", nullable = true)
    private String articleId;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "last_reviewed_at", nullable = true)
    private Instant lastReviewedAt;

    public FrontierClaimEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getClaimText() { return claimText; }
    public void setClaimText(String claimText) { this.claimText = claimText; }

    public LocalDate getClaimDate() { return claimDate; }
    public void setClaimDate(LocalDate claimDate) { this.claimDate = claimDate; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }

    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getEvidenceNotes() { return evidenceNotes; }
    public void setEvidenceNotes(String evidenceNotes) { this.evidenceNotes = evidenceNotes; }

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public void setLastReviewedAt(Instant lastReviewedAt) { this.lastReviewedAt = lastReviewedAt; }
}
