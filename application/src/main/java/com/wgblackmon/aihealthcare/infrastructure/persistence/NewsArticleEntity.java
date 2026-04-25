package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code news_articles} table.
 *
 * <p>This is a mutable infrastructure class that mirrors the immutable
 * {@link com.wgblackmon.aihealthcare.domain.model.NewsArticle} domain record.
 * All mapping between the two types happens inside
 * {@link ArticleStorageAdapter} and {@link ArticleIngestionAdapter} —
 * this entity never escapes into the domain or application layers.
 *
 * <p>The {@code url} field is stored as {@code VARCHAR(2048)} and serves as
 * the deduplication key.  {@link NewsArticleRepository#existsByUrl(String)}
 * is checked before each insert; duplicate URLs are silently skipped.
 *
 * <p>The {@code article_id} column is the primary key.  For RSS-harvested
 * articles it is populated from the feed entry URI or link URL; for manually
 * ingested articles it may be a generated UUID.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-20
 */
@Entity
@Table(name = "news_articles")
public class NewsArticleEntity {

    @Id
    @Column(name = "article_id", length = 512)
    private String articleId;

    @Column(length = 1024)
    private String title;

    @Column(length = 2048)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String bodyText;

    @Column(length = 512)
    private String topic;

    @Column(length = 512)
    private String author;

    private Long topicId;

    private String sourceName;

    private String sourceTier;

    private double sourceWeight;

    private Instant publishedAt;

    private Instant createdAt;

    /** Required no-arg constructor for JPA. */
    public NewsArticleEntity() {}

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getSourceTier() { return sourceTier; }
    public void setSourceTier(String sourceTier) { this.sourceTier = sourceTier; }

    public double getSourceWeight() { return sourceWeight; }
    public void setSourceWeight(double sourceWeight) { this.sourceWeight = sourceWeight; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
