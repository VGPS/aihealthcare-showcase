package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity representing a row in the {@code liability_incidents} table.
 *
 * <p>Enums ({@code incidentType}, {@code harmType}, {@code status}) are stored as
 * VARCHAR strings via {@code name()} / {@code valueOf()} in the adapter — no
 * {@code @Enumerated} annotation is used, matching the project convention.
 * All nullable domain fields map to nullable columns with no DB DEFAULT so that
 * Hibernate {@code ddl-auto: update} never attempts an ALTER on existing rows.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
@Entity
@Table(name = "liability_incidents")
public class LiabilityIncidentEntity {

    @Id
    @Column(name = "incident_id", length = 36)
    private String incidentId;

    @Column(name = "incident_type", nullable = false, length = 30)
    private String incidentType;

    @Column(name = "harm_type", nullable = false, length = 30)
    private String harmType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "plaintiff", length = 500)
    private String plaintiff;

    @Column(name = "defendant", nullable = false, length = 500)
    private String defendant;

    @Column(name = "legal_theory", length = 200)
    private String legalTheory;

    @Column(name = "court", length = 200)
    private String court;

    @Column(name = "case_number", length = 100)
    private String caseNumber;

    @Column(name = "harm_date")
    private LocalDate harmDate;

    @Column(name = "filing_date")
    private LocalDate filingDate;

    @Column(name = "outcome", length = 1000)
    private String outcome;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LiabilityIncidentEntity() {}

    public String getIncidentId()               { return incidentId; }
    public void   setIncidentId(String v)       { this.incidentId = v; }

    public String getIncidentType()             { return incidentType; }
    public void   setIncidentType(String v)     { this.incidentType = v; }

    public String getHarmType()                 { return harmType; }
    public void   setHarmType(String v)         { this.harmType = v; }

    public String getStatus()                   { return status; }
    public void   setStatus(String v)           { this.status = v; }

    public String getTitle()                    { return title; }
    public void   setTitle(String v)            { this.title = v; }

    public String getPlaintiff()                { return plaintiff; }
    public void   setPlaintiff(String v)        { this.plaintiff = v; }

    public String getDefendant()                { return defendant; }
    public void   setDefendant(String v)        { this.defendant = v; }

    public String getLegalTheory()              { return legalTheory; }
    public void   setLegalTheory(String v)      { this.legalTheory = v; }

    public String getCourt()                    { return court; }
    public void   setCourt(String v)            { this.court = v; }

    public String getCaseNumber()               { return caseNumber; }
    public void   setCaseNumber(String v)       { this.caseNumber = v; }

    public LocalDate getHarmDate()              { return harmDate; }
    public void      setHarmDate(LocalDate v)   { this.harmDate = v; }

    public LocalDate getFilingDate()            { return filingDate; }
    public void      setFilingDate(LocalDate v) { this.filingDate = v; }

    public String getOutcome()                  { return outcome; }
    public void   setOutcome(String v)          { this.outcome = v; }

    public String getSourceUrl()                { return sourceUrl; }
    public void   setSourceUrl(String v)        { this.sourceUrl = v; }

    public String getNotes()                    { return notes; }
    public void   setNotes(String v)            { this.notes = v; }

    public Instant getCreatedAt()               { return createdAt; }
    public void    setCreatedAt(Instant v)      { this.createdAt = v; }

    public Instant getUpdatedAt()               { return updatedAt; }
    public void    setUpdatedAt(Instant v)      { this.updatedAt = v; }
}
