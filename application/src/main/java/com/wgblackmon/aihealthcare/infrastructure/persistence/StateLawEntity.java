package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code state_laws} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.StateLaw}
 * domain record. Conversion is performed inside {@link StateLawAdapter}.
 * The {@code categories} list is stored as a pipe-delimited string of
 * {@link com.wgblackmon.aihealthcare.domain.model.LawCategory} enum names.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@Entity
@Table(name = "state_laws")
public class StateLawEntity {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode;

    @Column(name = "state_name", nullable = false, length = 100)
    private String stateName;

    @Column(name = "bill_number", nullable = false, length = 200)
    private String billNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "year_enacted", nullable = false)
    private int yearEnacted;

    @Column(name = "date_signed", length = 50)
    private String dateSigned;

    @Column(name = "date_signed_note", columnDefinition = "TEXT")
    private String dateSignedNote;

    @Column(name = "effective_date", length = 50)
    private String effectiveDate;

    @Column(name = "effective_date_note", columnDefinition = "TEXT")
    private String effectiveDateNote;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "status_detail", columnDefinition = "TEXT")
    private String statusDetail;

    @Column(name = "categories", length = 500)
    private String categories;

    @Column(name = "regulated_parties", columnDefinition = "TEXT")
    private String regulatedParties;

    @Column(name = "key_requirements", columnDefinition = "TEXT")
    private String keyRequirements;

    @Column(name = "enforcement", columnDefinition = "TEXT")
    private String enforcement;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "dataset_version", length = 20)
    private String datasetVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required no-arg constructor for JPA. */
    public StateLawEntity() {}

    public String getId()                                   { return id; }
    public void setId(String id)                            { this.id = id; }

    public String getStateCode()                            { return stateCode; }
    public void setStateCode(String stateCode)              { this.stateCode = stateCode; }

    public String getStateName()                            { return stateName; }
    public void setStateName(String stateName)              { this.stateName = stateName; }

    public String getBillNumber()                           { return billNumber; }
    public void setBillNumber(String billNumber)            { this.billNumber = billNumber; }

    public String getTitle()                                { return title; }
    public void setTitle(String title)                      { this.title = title; }

    public int getYearEnacted()                             { return yearEnacted; }
    public void setYearEnacted(int yearEnacted)             { this.yearEnacted = yearEnacted; }

    public String getDateSigned()                           { return dateSigned; }
    public void setDateSigned(String dateSigned)            { this.dateSigned = dateSigned; }

    public String getDateSignedNote()                       { return dateSignedNote; }
    public void setDateSignedNote(String dateSignedNote)    { this.dateSignedNote = dateSignedNote; }

    public String getEffectiveDate()                        { return effectiveDate; }
    public void setEffectiveDate(String effectiveDate)      { this.effectiveDate = effectiveDate; }

    public String getEffectiveDateNote()                    { return effectiveDateNote; }
    public void setEffectiveDateNote(String effectiveDateNote) { this.effectiveDateNote = effectiveDateNote; }

    public String getStatus()                               { return status; }
    public void setStatus(String status)                    { this.status = status; }

    public String getStatusDetail()                         { return statusDetail; }
    public void setStatusDetail(String statusDetail)        { this.statusDetail = statusDetail; }

    public String getCategories()                           { return categories; }
    public void setCategories(String categories)            { this.categories = categories; }

    public String getRegulatedParties()                     { return regulatedParties; }
    public void setRegulatedParties(String regulatedParties) { this.regulatedParties = regulatedParties; }

    public String getKeyRequirements()                      { return keyRequirements; }
    public void setKeyRequirements(String keyRequirements)  { this.keyRequirements = keyRequirements; }

    public String getEnforcement()                          { return enforcement; }
    public void setEnforcement(String enforcement)          { this.enforcement = enforcement; }

    public String getNotes()                                { return notes; }
    public void setNotes(String notes)                      { this.notes = notes; }

    public String getDatasetVersion()                       { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion)    { this.datasetVersion = datasetVersion; }

    public Instant getCreatedAt()                           { return createdAt; }
    public void setCreatedAt(Instant createdAt)             { this.createdAt = createdAt; }

    public Instant getUpdatedAt()                           { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt)             { this.updatedAt = updatedAt; }
}
