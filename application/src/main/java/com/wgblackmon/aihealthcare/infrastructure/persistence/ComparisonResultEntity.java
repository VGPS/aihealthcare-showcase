package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code comparison_results} table.
 *
 * <p>A comparison groups multiple {@link EvaluationResultEntity} rows that
 * share the same {@code comparison_id}.  The linked evaluations are
 * reassembled by the adapter via a query on {@code EvaluationResultRepository}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@Entity
@Table(name = "comparison_results")
public class ComparisonResultEntity {

    @Id
    @Column(name = "comparison_id")
    private String comparisonId;

    @Column(name = "article_ids", columnDefinition = "TEXT")
    private String articleIdsJoined;

    private String topic;

    private String tone;

    @Column(name = "compared_at")
    private Instant comparedAt;

    /** Required no-arg constructor for JPA. */
    public ComparisonResultEntity() {}

    public String getComparisonId() { return comparisonId; }
    public void setComparisonId(String comparisonId) { this.comparisonId = comparisonId; }

    public String getArticleIdsJoined() { return articleIdsJoined; }
    public void setArticleIdsJoined(String articleIdsJoined) { this.articleIdsJoined = articleIdsJoined; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public Instant getComparedAt() { return comparedAt; }
    public void setComparedAt(Instant comparedAt) { this.comparedAt = comparedAt; }
}
