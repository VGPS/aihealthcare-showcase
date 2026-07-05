package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a wiki lint run in the {@code lint_reports} table.
 *
 * <p>Each lint run produces a durable report recording which pages have
 * quality issues (orphaned, broken refs, stale, missing provenance).
 * Slug lists are stored as pipe-delimited strings, matching the project's
 * existing pattern (see {@link CompilationReportEntity}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
@Entity
@Table(name = "lint_reports")
public class LintReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_started_at", nullable = false)
    private Instant runStartedAt;

    @Column(name = "run_completed_at", nullable = false)
    private Instant runCompletedAt;

    @Column(name = "total_pages_checked", nullable = false)
    private int totalPagesChecked;

    @Column(name = "orphaned_slugs", columnDefinition = "TEXT")
    private String orphanedSlugs;

    @Column(name = "broken_refs", columnDefinition = "TEXT")
    private String brokenRefs;

    @Column(name = "stale_slugs", columnDefinition = "TEXT")
    private String staleSlugs;

    @Column(name = "missing_provenance", columnDefinition = "TEXT")
    private String missingProvenance;

    @Column(columnDefinition = "TEXT")
    private String warnings;

    /** Required no-arg constructor for JPA. */
    public LintReportEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getRunStartedAt() { return runStartedAt; }
    public void setRunStartedAt(Instant runStartedAt) { this.runStartedAt = runStartedAt; }

    public Instant getRunCompletedAt() { return runCompletedAt; }
    public void setRunCompletedAt(Instant runCompletedAt) { this.runCompletedAt = runCompletedAt; }

    public int getTotalPagesChecked() { return totalPagesChecked; }
    public void setTotalPagesChecked(int totalPagesChecked) { this.totalPagesChecked = totalPagesChecked; }

    public String getOrphanedSlugs() { return orphanedSlugs; }
    public void setOrphanedSlugs(String orphanedSlugs) { this.orphanedSlugs = orphanedSlugs; }

    public String getBrokenRefs() { return brokenRefs; }
    public void setBrokenRefs(String brokenRefs) { this.brokenRefs = brokenRefs; }

    public String getStaleSlugs() { return staleSlugs; }
    public void setStaleSlugs(String staleSlugs) { this.staleSlugs = staleSlugs; }

    public String getMissingProvenance() { return missingProvenance; }
    public void setMissingProvenance(String missingProvenance) { this.missingProvenance = missingProvenance; }

    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }
}
