package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code clinical_trials} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.ClinicalTrial}
 * domain record. Conversion is performed inside {@link ClinicalTrialAdapter}.
 * The {@code conditions} and {@code aiHealthcareKeywords} lists are stored as
 * pipe-delimited strings.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@Entity
@Table(name = "clinical_trials")
public class ClinicalTrialEntity {

    @Id
    @Column(name = "trial_id", nullable = false, length = 36)
    private String trialId;

    @Column(name = "nct_id", nullable = false, unique = true, length = 20)
    private String nctId;

    @Column(name = "title", nullable = false, length = 1000)
    private String title;

    @Column(name = "sponsor", length = 500)
    private String sponsor;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "phase", length = 30)
    private String phase;

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "brief_summary", columnDefinition = "TEXT")
    private String briefSummary;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "study_type", length = 30)
    private String studyType;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "ai_healthcare_keywords", columnDefinition = "TEXT")
    private String aiHealthcareKeywords;

    /** Required no-arg constructor for JPA. */
    public ClinicalTrialEntity() {}

    public String getTrialId()                                 { return trialId; }
    public void setTrialId(String trialId)                     { this.trialId = trialId; }

    public String getNctId()                                   { return nctId; }
    public void setNctId(String nctId)                         { this.nctId = nctId; }

    public String getTitle()                                   { return title; }
    public void setTitle(String title)                         { this.title = title; }

    public String getSponsor()                                 { return sponsor; }
    public void setSponsor(String sponsor)                     { this.sponsor = sponsor; }

    public String getStatus()                                  { return status; }
    public void setStatus(String status)                       { this.status = status; }

    public String getPhase()                                   { return phase; }
    public void setPhase(String phase)                         { this.phase = phase; }

    public String getConditions()                              { return conditions; }
    public void setConditions(String conditions)               { this.conditions = conditions; }

    public String getBriefSummary()                            { return briefSummary; }
    public void setBriefSummary(String briefSummary)           { this.briefSummary = briefSummary; }

    public String getSourceUrl()                               { return sourceUrl; }
    public void setSourceUrl(String sourceUrl)                 { this.sourceUrl = sourceUrl; }

    public String getStudyType()                               { return studyType; }
    public void setStudyType(String studyType)                 { this.studyType = studyType; }

    public Instant getStartDate()                              { return startDate; }
    public void setStartDate(Instant startDate)                { this.startDate = startDate; }

    public Instant getDiscoveredAt()                           { return discoveredAt; }
    public void setDiscoveredAt(Instant discoveredAt)          { this.discoveredAt = discoveredAt; }

    public String getAiHealthcareKeywords()                    { return aiHealthcareKeywords; }
    public void setAiHealthcareKeywords(String keywords)       { this.aiHealthcareKeywords = keywords; }
}
