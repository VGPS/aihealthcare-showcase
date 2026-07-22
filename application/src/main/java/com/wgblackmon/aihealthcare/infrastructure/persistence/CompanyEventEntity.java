package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code company_events} table.
 *
 * <p>Each event links back to a {@link CompanyProfileEntity} via companySlug
 * and to a source article via sourceArticleId.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Entity
@Table(name = "company_events")
public class CompanyEventEntity {

    @Id
    private String eventId;

    private String companySlug;

    private String eventType;

    @Column(length = 1024)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String sourceArticleId;

    private Instant occurredAt;

    private Instant detectedAt;

    /** Required no-arg constructor for JPA. */
    public CompanyEventEntity() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getCompanySlug() { return companySlug; }
    public void setCompanySlug(String companySlug) { this.companySlug = companySlug; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourceArticleId() { return sourceArticleId; }
    public void setSourceArticleId(String sourceArticleId) { this.sourceArticleId = sourceArticleId; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
}
