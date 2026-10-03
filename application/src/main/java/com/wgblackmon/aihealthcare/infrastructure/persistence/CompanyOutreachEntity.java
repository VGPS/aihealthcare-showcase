package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code company_outreach} table.
 *
 * <p>One row per company per purpose (enforced by unique constraint).
 * Stored separately from {@code company_profiles} so pipeline upserts
 * never touch outreach state.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
@Entity
@Table(name = "company_outreach",
       uniqueConstraints = @UniqueConstraint(columnNames = {"slug", "purpose"}))
public class CompanyOutreachEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String purpose;

    @Column(nullable = false)
    private String status;

    private Instant contactedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Required no-arg constructor for JPA. */
    public CompanyOutreachEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getContactedAt() { return contactedAt; }
    public void setContactedAt(Instant contactedAt) { this.contactedAt = contactedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
