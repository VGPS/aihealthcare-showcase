package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code regulatory_events} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent}
 * domain record. Conversion is performed inside {@link RegulatoryEventAdapter}.
 * The {@code aiHealthcareKeywords} list is stored as a pipe-delimited string.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-30
 */
@Entity
@Table(name = "regulatory_events")
public class RegulatoryEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "regulatory_body", nullable = false, length = 20)
    private String regulatoryBody;

    @Column(name = "title", nullable = false, length = 1000)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    @Column(name = "applicant_name", length = 500)
    private String applicantName;

    @Column(name = "device_name", length = 500)
    private String deviceName;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "linked_article_id", length = 255)
    private String linkedArticleId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "ai_healthcare_keywords", columnDefinition = "TEXT")
    private String aiHealthcareKeywords;

    @Column(name = "outcome_status", length = 30)
    private String outcomeStatus;

    @Column(name = "outcome_updated_at")
    private Instant outcomeUpdatedAt;

    @Column(name = "clearance_type", length = 50)
    private String clearanceType;

    @Column(name = "predicate_device_number", length = 50)
    private String predicateDeviceNumber;

    /** Required no-arg constructor for JPA. */
    public RegulatoryEventEntity() {}

    public String getEventId()                              { return eventId; }
    public void setEventId(String eventId)                  { this.eventId = eventId; }

    public String getEventType()                            { return eventType; }
    public void setEventType(String eventType)              { this.eventType = eventType; }

    public String getRegulatoryBody()                       { return regulatoryBody; }
    public void setRegulatoryBody(String regulatoryBody)    { this.regulatoryBody = regulatoryBody; }

    public String getTitle()                                { return title; }
    public void setTitle(String title)                      { this.title = title; }

    public String getSummary()                              { return summary; }
    public void setSummary(String summary)                  { this.summary = summary; }

    public String getReferenceNumber()                      { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber)  { this.referenceNumber = referenceNumber; }

    public String getApplicantName()                        { return applicantName; }
    public void setApplicantName(String applicantName)      { this.applicantName = applicantName; }

    public String getDeviceName()                           { return deviceName; }
    public void setDeviceName(String deviceName)            { this.deviceName = deviceName; }

    public String getSourceUrl()                            { return sourceUrl; }
    public void setSourceUrl(String sourceUrl)              { this.sourceUrl = sourceUrl; }

    public String getLinkedArticleId()                      { return linkedArticleId; }
    public void setLinkedArticleId(String linkedArticleId)  { this.linkedArticleId = linkedArticleId; }

    public Instant getPublishedAt()                         { return publishedAt; }
    public void setPublishedAt(Instant publishedAt)         { this.publishedAt = publishedAt; }

    public Instant getDiscoveredAt()                        { return discoveredAt; }
    public void setDiscoveredAt(Instant discoveredAt)       { this.discoveredAt = discoveredAt; }

    public String getAiHealthcareKeywords()                 { return aiHealthcareKeywords; }
    public void setAiHealthcareKeywords(String keywords)    { this.aiHealthcareKeywords = keywords; }

    public String getOutcomeStatus()                                    { return outcomeStatus; }
    public void setOutcomeStatus(String outcomeStatus)                  { this.outcomeStatus = outcomeStatus; }

    public Instant getOutcomeUpdatedAt()                                { return outcomeUpdatedAt; }
    public void setOutcomeUpdatedAt(Instant outcomeUpdatedAt)           { this.outcomeUpdatedAt = outcomeUpdatedAt; }

    public String getClearanceType()                                    { return clearanceType; }
    public void setClearanceType(String clearanceType)                  { this.clearanceType = clearanceType; }

    public String getPredicateDeviceNumber()                            { return predicateDeviceNumber; }
    public void setPredicateDeviceNumber(String predicateDeviceNumber)  { this.predicateDeviceNumber = predicateDeviceNumber; }
}
