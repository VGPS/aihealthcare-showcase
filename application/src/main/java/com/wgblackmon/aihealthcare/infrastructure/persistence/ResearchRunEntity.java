package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code research_runs} table.
 *
 * <p>This is a mutable infrastructure class that mirrors the immutable
 * {@link com.wgblackmon.aihealthcare.domain.model.ResearchRun} domain record.
 * All mapping between the two types happens inside {@link ResearchRunAdapter}.
 *
 * <p>One row is written at the end of every successful research pipeline
 * execution, giving operators a queryable audit trail of all research activity.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
@Entity
@Table(name = "research_runs")
public class ResearchRunEntity {

    @Id
    @Column(name = "run_id")
    private String runId;

    @Column(name = "query", columnDefinition = "TEXT")
    private String query;

    @Column(name = "mode")
    private String mode;

    @Column(name = "citation_count")
    private int citationCount;

    @Column(name = "researched_at")
    private Instant researchedAt;

    /** Required no-arg constructor for JPA. */
    public ResearchRunEntity() {}

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public int getCitationCount() { return citationCount; }
    public void setCitationCount(int citationCount) { this.citationCount = citationCount; }

    public Instant getResearchedAt() { return researchedAt; }
    public void setResearchedAt(Instant researchedAt) { this.researchedAt = researchedAt; }
}
