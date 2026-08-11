package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code wiki_gap_runs} table.
 *
 * <p>Each row records a single execution of the wiki gap analysis pipeline,
 * including timing, counts, and an overall coverage summary.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
@Entity
@Table(name = "wiki_gap_runs")
public class WikiGapRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "articles_analyzed", nullable = false)
    private int articlesAnalyzed;

    @Column(name = "wiki_pages_checked", nullable = false)
    private int wikiPagesChecked;

    @Column(name = "gaps_found", nullable = false)
    private int gapsFound;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 20, nullable = false)
    private String status;

    /** Required no-arg constructor for JPA. */
    public WikiGapRunEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public int getArticlesAnalyzed() { return articlesAnalyzed; }
    public void setArticlesAnalyzed(int articlesAnalyzed) { this.articlesAnalyzed = articlesAnalyzed; }

    public int getWikiPagesChecked() { return wikiPagesChecked; }
    public void setWikiPagesChecked(int wikiPagesChecked) { this.wikiPagesChecked = wikiPagesChecked; }

    public int getGapsFound() { return gapsFound; }
    public void setGapsFound(int gapsFound) { this.gapsFound = gapsFound; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
