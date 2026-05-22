package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code topic_summaries} table.
 *
 * <p>This is a mutable infrastructure class that mirrors the immutable
 * {@link com.wgblackmon.aihealthcare.domain.model.TopicSummary} domain record.
 * All mapping between the two types happens inside {@link TopicSummaryAdapter}.
 *
 * <p>The {@code topic} column is the primary key — only one summary exists
 * per topic at any time.  Saving with the same topic overwrites the previous
 * summary (upsert semantics).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-21
 * @updated 2026-05-21
 */
@Entity
@Table(name = "topic_summaries")
public class TopicSummaryEntity {

    @Id
    @Column(name = "topic", length = 512)
    private String topic;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "generated_at")
    private Instant generatedAt;

    /** Required no-arg constructor for JPA. */
    public TopicSummaryEntity() {}

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
