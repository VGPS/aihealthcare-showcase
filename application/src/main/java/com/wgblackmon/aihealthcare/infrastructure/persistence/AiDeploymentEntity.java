package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity representing a row in the {@code ai_deployments} table.
 *
 * <p>Catalogs AI deployments at health systems by domain: CLINICAL,
 * REVENUE_CYCLE, FINANCIAL_SCORING, or RESEARCH. The FINANCIAL_SCORING
 * domain (charity-eligibility scoring, propensity-to-pay AI) is the
 * least-covered and most ethically loaded category.
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
@Entity
@Table(name = "ai_deployments")
public class AiDeploymentEntity {

    @Id
    private String id;

    private String healthSystemId;

    private String vendor;

    private String product;

    private String domain;

    @Column(columnDefinition = "TEXT")
    private String scaleMetric;

    @Column(columnDefinition = "TEXT")
    private String sourceUrl;

    private LocalDate deployedDate;

    private Instant createdAt;

    /** Required no-arg constructor for JPA. */
    public AiDeploymentEntity() {}

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHealthSystemId() { return healthSystemId; }
    public void setHealthSystemId(String healthSystemId) { this.healthSystemId = healthSystemId; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getScaleMetric() { return scaleMetric; }
    public void setScaleMetric(String scaleMetric) { this.scaleMetric = scaleMetric; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public LocalDate getDeployedDate() { return deployedDate; }
    public void setDeployedDate(LocalDate deployedDate) { this.deployedDate = deployedDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
