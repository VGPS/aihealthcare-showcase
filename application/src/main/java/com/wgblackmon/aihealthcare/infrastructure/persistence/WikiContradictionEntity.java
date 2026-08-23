package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a detected contradiction in the {@code wiki_contradictions} table.
 *
 * <p>Stores the prior and new claims along with pipe-delimited article IDs
 * for provenance on both sides.  This matches the project's existing pattern
 * for multi-value storage (see {@link EvaluationResultEntity}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-08-23
 */
@Entity
@Table(name = "wiki_contradictions",
       indexes = @Index(name = "idx_wiki_contradictions_page_slug", columnList = "page_slug"))
public class WikiContradictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_slug", length = 255, nullable = false)
    private String pageSlug;

    @Column(name = "prior_claim", columnDefinition = "TEXT", nullable = false)
    private String priorClaim;

    @Column(name = "new_claim", columnDefinition = "TEXT", nullable = false)
    private String newClaim;

    @Column(name = "prior_source_ids", columnDefinition = "TEXT")
    private String priorSourceIds;

    @Column(name = "new_source_ids", columnDefinition = "TEXT")
    private String newSourceIds;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** Required no-arg constructor for JPA. */
    public WikiContradictionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPageSlug() { return pageSlug; }
    public void setPageSlug(String pageSlug) { this.pageSlug = pageSlug; }

    public String getPriorClaim() { return priorClaim; }
    public void setPriorClaim(String priorClaim) { this.priorClaim = priorClaim; }

    public String getNewClaim() { return newClaim; }
    public void setNewClaim(String newClaim) { this.newClaim = newClaim; }

    public String getPriorSourceIds() { return priorSourceIds; }
    public void setPriorSourceIds(String priorSourceIds) { this.priorSourceIds = priorSourceIds; }

    public String getNewSourceIds() { return newSourceIds; }
    public void setNewSourceIds(String newSourceIds) { this.newSourceIds = newSourceIds; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
}
