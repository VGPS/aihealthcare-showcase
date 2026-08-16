package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity representing a row in the {@code enforcement_actions} table.
 *
 * <p>Records federal and state enforcement actions (DOJ, HHS-OIG, state AGs,
 * FTC, CMS) against health systems. {@code entityNameAtTime} preserves the
 * legal name at the time of the action — critical because successor names
 * differ (e.g. "Adventist Health System" vs. "AdventHealth").
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
@Entity
@Table(name = "enforcement_actions")
public class EnforcementActionEntity {

    @Id
    private String id;

    private String healthSystemId;

    private String entityNameAtTime;

    private String agency;

    private String theory;

    private Long amountUsd;

    private LocalDate settlementDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String sourceUrl;

    private Instant createdAt;

    /** Required no-arg constructor for JPA. */
    public EnforcementActionEntity() {}

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHealthSystemId() { return healthSystemId; }
    public void setHealthSystemId(String healthSystemId) { this.healthSystemId = healthSystemId; }

    public String getEntityNameAtTime() { return entityNameAtTime; }
    public void setEntityNameAtTime(String entityNameAtTime) { this.entityNameAtTime = entityNameAtTime; }

    public String getAgency() { return agency; }
    public void setAgency(String agency) { this.agency = agency; }

    public String getTheory() { return theory; }
    public void setTheory(String theory) { this.theory = theory; }

    public Long getAmountUsd() { return amountUsd; }
    public void setAmountUsd(Long amountUsd) { this.amountUsd = amountUsd; }

    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
