package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code perplexity_citations} table.
 *
 * <p>Each row is a citation URL logged from a Perplexity API call during the
 * company discovery pipeline. Links back to a company via {@code companyId}
 * (nullable for discovery-phase citations where no company is yet identified).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
@Entity
@Table(name = "perplexity_citations")
public class PerplexityCitationEntity {

    @Id
    private String citationId;

    private String companyId;

    @Column(length = 2048)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(length = 20)
    private String callType;

    private Instant retrievedAt;

    /** Required no-arg constructor for JPA. */
    public PerplexityCitationEntity() {}

    public String getCitationId() { return citationId; }
    public void setCitationId(String citationId) { this.citationId = citationId; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }

    public Instant getRetrievedAt() { return retrievedAt; }
    public void setRetrievedAt(Instant retrievedAt) { this.retrievedAt = retrievedAt; }
}
