package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a wiki compilation run in the {@code compilation_reports} table.
 *
 * <p>Each compilation run produces a durable report recording which pages were
 * created or updated, how many articles were processed, and any warnings.
 * Slug lists are stored as pipe-delimited strings, matching the project's
 * existing pattern (see {@link EvaluationResultEntity}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Entity
@Table(name = "compilation_reports")
public class CompilationReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_started_at", nullable = false)
    private Instant runStartedAt;

    @Column(name = "run_completed_at", nullable = false)
    private Instant runCompletedAt;

    @Column(name = "articles_processed", nullable = false)
    private int articlesProcessed;

    @Column(name = "pages_created", columnDefinition = "TEXT")
    private String pagesCreated;

    @Column(name = "pages_updated", columnDefinition = "TEXT")
    private String pagesUpdated;

    @Column(name = "contradiction_count")
    private int contradictionCount;

    @Column(columnDefinition = "TEXT")
    private String warnings;

    /** Required no-arg constructor for JPA. */
    public CompilationReportEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getRunStartedAt() { return runStartedAt; }
    public void setRunStartedAt(Instant runStartedAt) { this.runStartedAt = runStartedAt; }

    public Instant getRunCompletedAt() { return runCompletedAt; }
    public void setRunCompletedAt(Instant runCompletedAt) { this.runCompletedAt = runCompletedAt; }

    public int getArticlesProcessed() { return articlesProcessed; }
    public void setArticlesProcessed(int articlesProcessed) { this.articlesProcessed = articlesProcessed; }

    public String getPagesCreated() { return pagesCreated; }
    public void setPagesCreated(String pagesCreated) { this.pagesCreated = pagesCreated; }

    public String getPagesUpdated() { return pagesUpdated; }
    public void setPagesUpdated(String pagesUpdated) { this.pagesUpdated = pagesUpdated; }

    public int getContradictionCount() { return contradictionCount; }
    public void setContradictionCount(int contradictionCount) { this.contradictionCount = contradictionCount; }

    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }
}
