package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code wiki_pages} table.
 *
 * <p>This is a mutable infrastructure class that mirrors the immutable
 * {@link com.wgblackmon.aihealthcare.domain.model.WikiPage} domain record.
 * All mapping between the two types happens inside the wiki adapters —
 * this entity never escapes into the domain or application layers.
 *
 * <p>The {@code slug} field is the natural primary key (kebab-case, unique).
 * Tags and related slugs are stored as pipe-delimited strings for simplicity,
 * matching the project's existing pattern for multi-value text columns.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Entity
@Table(name = "wiki_pages")
public class WikiPageEntity {

    @Id
    @Column(name = "slug", length = 255)
    private String slug;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(name = "page_type", length = 50, nullable = false)
    private String pageType;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(name = "content_markdown", columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(name = "related_slugs", columnDefinition = "TEXT")
    private String relatedSlugs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(nullable = false)
    private int revision = 1;

    /** Required no-arg constructor for JPA. */
    public WikiPageEntity() {}

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }

    public String getRelatedSlugs() { return relatedSlugs; }
    public void setRelatedSlugs(String relatedSlugs) { this.relatedSlugs = relatedSlugs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
}
