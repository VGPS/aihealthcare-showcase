package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code healthcare_ai_companies} table.
 *
 * <p>Stores AI-in-healthcare company data discovered and enriched via the
 * Perplexity API pipeline. Source URLs and validation sources are stored
 * as pipe-delimited strings.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
@Entity
@Table(name = "healthcare_ai_companies")
public class HealthcareAiCompanyEntity {

    @Id
    private String companyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nameNormalized;

    private String domain;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String hqLocation;

    private Integer foundedYear;

    @Column(length = 100)
    private String sector;

    @Column(length = 100)
    private String subSector;

    @Column(length = 50)
    private String fundingStage;

    @Column(length = 100)
    private String estimatedFunding;

    @Column(columnDefinition = "TEXT")
    private String foundersJson;

    @Column(columnDefinition = "TEXT")
    private String sourceUrlsPipe;

    private boolean validated;

    @Column(columnDefinition = "TEXT")
    private String validationSourcesPipe;

    private Instant discoveredAt;

    private Instant lastValidatedAt;

    /** Required no-arg constructor for JPA. */
    public HealthcareAiCompanyEntity() {}

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNameNormalized() { return nameNormalized; }
    public void setNameNormalized(String nameNormalized) { this.nameNormalized = nameNormalized; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHqLocation() { return hqLocation; }
    public void setHqLocation(String hqLocation) { this.hqLocation = hqLocation; }

    public Integer getFoundedYear() { return foundedYear; }
    public void setFoundedYear(Integer foundedYear) { this.foundedYear = foundedYear; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public String getSubSector() { return subSector; }
    public void setSubSector(String subSector) { this.subSector = subSector; }

    public String getFundingStage() { return fundingStage; }
    public void setFundingStage(String fundingStage) { this.fundingStage = fundingStage; }

    public String getEstimatedFunding() { return estimatedFunding; }
    public void setEstimatedFunding(String estimatedFunding) { this.estimatedFunding = estimatedFunding; }

    public String getFoundersJson() { return foundersJson; }
    public void setFoundersJson(String foundersJson) { this.foundersJson = foundersJson; }

    public String getSourceUrlsPipe() { return sourceUrlsPipe; }
    public void setSourceUrlsPipe(String sourceUrlsPipe) { this.sourceUrlsPipe = sourceUrlsPipe; }

    public boolean isValidated() { return validated; }
    public void setValidated(boolean validated) { this.validated = validated; }

    public String getValidationSourcesPipe() { return validationSourcesPipe; }
    public void setValidationSourcesPipe(String validationSourcesPipe) { this.validationSourcesPipe = validationSourcesPipe; }

    public Instant getDiscoveredAt() { return discoveredAt; }
    public void setDiscoveredAt(Instant discoveredAt) { this.discoveredAt = discoveredAt; }

    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public void setLastValidatedAt(Instant lastValidatedAt) { this.lastValidatedAt = lastValidatedAt; }
}
