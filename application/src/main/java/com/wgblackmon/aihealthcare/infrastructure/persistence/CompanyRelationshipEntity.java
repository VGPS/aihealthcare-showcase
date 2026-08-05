package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code company_relationships} table.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Entity
@Table(name = "company_relationships")
public class CompanyRelationshipEntity {

    @Id
    @Column(name = "relationship_id", nullable = false, length = 36)
    private String relationshipId;

    @Column(name = "source_company", nullable = false, length = 500)
    private String sourceCompany;

    @Column(name = "target_company", nullable = false, length = 500)
    private String targetCompany;

    @Column(name = "relationship_type", nullable = false, length = 30)
    private String relationshipType;

    @Column(name = "evidence_article_id", length = 500)
    private String evidenceArticleId;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    public CompanyRelationshipEntity() {}

    public String getRelationshipId()                              { return relationshipId; }
    public void setRelationshipId(String relationshipId)           { this.relationshipId = relationshipId; }

    public String getSourceCompany()                               { return sourceCompany; }
    public void setSourceCompany(String sourceCompany)             { this.sourceCompany = sourceCompany; }

    public String getTargetCompany()                               { return targetCompany; }
    public void setTargetCompany(String targetCompany)             { this.targetCompany = targetCompany; }

    public String getRelationshipType()                            { return relationshipType; }
    public void setRelationshipType(String relationshipType)       { this.relationshipType = relationshipType; }

    public String getEvidenceArticleId()                           { return evidenceArticleId; }
    public void setEvidenceArticleId(String evidenceArticleId)     { this.evidenceArticleId = evidenceArticleId; }

    public String getSummary()                                     { return summary; }
    public void setSummary(String summary)                         { this.summary = summary; }

    public double getConfidence()                                  { return confidence; }
    public void setConfidence(double confidence)                   { this.confidence = confidence; }

    public Instant getDetectedAt()                                 { return detectedAt; }
    public void setDetectedAt(Instant detectedAt)                  { this.detectedAt = detectedAt; }
}
