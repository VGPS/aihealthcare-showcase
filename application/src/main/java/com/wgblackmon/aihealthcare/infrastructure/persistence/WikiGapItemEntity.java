package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code wiki_gap_items} table.
 *
 * <p>Each row records a single knowledge gap identified during a gap analysis
 * run — a topic covered by articles but missing from the wiki. The
 * {@code articleIds} field stores pipe-delimited article IDs, matching the
 * existing wiki pattern in {@link WikiPageEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
@Entity
@Table(name = "wiki_gap_items")
public class WikiGapItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(length = 500, nullable = false)
    private String topic;

    @Column(name = "article_ids", columnDefinition = "TEXT")
    private String articleIds;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Required no-arg constructor for JPA. */
    public WikiGapItemEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getArticleIds() { return articleIds; }
    public void setArticleIds(String articleIds) { this.articleIds = articleIds; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
